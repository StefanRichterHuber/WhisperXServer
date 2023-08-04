package com.github.StefanRichterHuber.WhisperXServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.StefanRichterHuber.WhisperXServer.WhisperXOutput.Segment;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class WhisperXService {

	@Inject
	Logger logger;

	private String workdir = "/tmp";
	private String computeType = "int8";

	private static final String TASK_TRANSCRIBE = "transcribe";
	private static final String TASK_TRANSLATE = "translate";

	private Executor executor;

	/**
	 * WhisperX executable to use
	 */
	@Inject
	@ConfigProperty(name = "whisperx.executable", defaultValue = "whisperx")
	String executable;

	/**
	 * Number of parallel whisperX instances to run. Further jobs get queued.
	 */
	@Inject
	@ConfigProperty(name = "whisperx.parallel-instances", defaultValue = "1")
	int numOfInstances;

	/**
	 * WhisperX model to use. (small, medium, large-v2)
	 */
	@Inject
	@ConfigProperty(name = "whisperx.model", defaultValue = "small")
	String whisperXModel;

	/**
	 * Hugging Face Access Token to access PyAnnote gated models (default: None)
	 * 
	 * @see https://github.com/m-bain/whisperX
	 */
	@Inject
	@ConfigProperty(name = "whisperx.hf-token")
	Optional<String> hfToken;

	/**
	 * number of threads used by torch for CPU inference; supercedes
	 * MKL_NUM_THREADS/OMP_NUM_THREADS (default: 0)
	 */
	@Inject
	@ConfigProperty(name = "whisperx.threads")
	Optional<Integer> whisperXThreads;

	/**
	 * Always apply diarization to assign speaker labels to each segment/word
	 * (default:
	 * False). Can also be requested per call.
	 */
	@Inject
	@ConfigProperty(name = "whisperx.diarize", defaultValue = "false")
	boolean diarize;

	@PostConstruct
	void createExecutor() {
		/**
		 * Since whisperX uses a lot of compute power and memory, we limit the amount of
		 * parallel executions.
		 */
		this.executor = Executors.newFixedThreadPool(numOfInstances > 0 ? numOfInstances : 1);
	}

	/**
	 * Transcribes the given audio content
	 * 
	 * @param content      Audio file in wav format, with 16.000 kHz and mono
	 * @param language     language spoken in the audio, specify null to perform
	 *                     language detection
	 * @param outputFormat One of srt,vtt,txt,tsv,json,aud
	 * @param diarize      Apply diarization to assign speaker labels to each
	 *                     segment/word
	 * @return String containing the result file
	 */

	public CompletableFuture<String> transcribe(byte[] content, boolean diarize, String language, String outputFormat) {
		return invokeWisperX(content, diarize, language, outputFormat, TASK_TRANSCRIBE);
	}

	/**
	 * Translates the given audio content to English
	 * 
	 * @param content      Audio file in wav format, with 16.000 kHz and mono
	 * @param language     language spoken in the audio, specify null to perform
	 *                     language detection
	 * @param outputFormat One of srt,vtt,txt,tsv,json,aud
	 * @param diarize      Apply diarization to assign speaker labels to each
	 *                     segment/word
	 * 
	 * @return String containing the result file
	 */

	public CompletableFuture<String> translate(byte[] content, boolean diarize, String language, String outputFormat) {
		return invokeWisperX(content, diarize, language, outputFormat, TASK_TRANSLATE);
	}

	/**
	 * Converts a transcription model to another format
	 * 
	 * @param input        JSON output from WhisperXOutput
	 * @param outputFormat One of srt,vtt,txt,tsv,json,aud
	 * @return
	 * @throws JsonProcessingException
	 */
	public String convertTranscription(WhisperXOutput input, String outputFormat) throws JsonProcessingException {
		if ("json".equals(outputFormat)) {
			final ObjectMapper om = new ObjectMapper();
			final String result = om.writeValueAsString(input);
			return result;
		}
		if ("txt".equals(outputFormat)) {
			String currentSpeaker = null;
			final StringBuilder sb = new StringBuilder();

			for (Segment segment : input.getSegments()) {
				final String speaker = segment.getSpeaker();
				// Header line for each speaker
				if (speaker != null && !speaker.isBlank() && !Objects.equals(currentSpeaker, speaker)) {
					if (!sb.isEmpty()) {
						sb.append("\n\n");
					}
					sb.append(speaker).append(":").append("\n");
					currentSpeaker = speaker;
				}
				final String text = segment.getText().trim();
				sb.append(text);
				if (text.endsWith("!") || text.endsWith("?")
						|| text.endsWith(".")) {
					sb.append(" ");
				}
			}
			return sb.toString();
		}
		logger.errorf("Output format '%s' not supported for conversion", outputFormat);
		return null;
	}

	/**
	 * Invokes whisperX
	 * 
	 * @param content      Audio file in wav format, with 16.000 kHz and mono
	 * @param language     language spoken in the audio, specify null to perform
	 *                     language detection
	 * @param outputFormat One of srt,vtt,txt,tsv,json,aud
	 * @param task         One of transcribe,translate
	 * @param diarize      Apply diarization to assign speaker
	 *                     labels to each segment/word
	 * 
	 * @return String containing the result file
	 */

	private CompletableFuture<String> invokeWisperX(byte[] content, boolean diarize, String language,
			String outputFormat, String task) {

		final String filePrefix = UUID.randomUUID().toString();
		final String sourceFile = workdir + "/" + filePrefix + ".wav";
		final String resultFile = workdir + "/" + filePrefix + "." + outputFormat;

		return CompletableFuture.supplyAsync(() -> {
			logger.infof(
					"Invoked whisperX service with task '%s' in language '%s' for input '%s' and output '%s' in the format '%s'",
					task, language, sourceFile, resultFile, outputFormat);
			// Write temporary file
			try {
				Files.write(Paths.get(sourceFile), content);
			} catch (IOException e) {
				throw new RuntimeException("Failed to write source file " + sourceFile, e);
			}

			// Invoke whisperX
			try {
				final Process process = new ProcessBuilder(
						buildProcessInvocation(language, diarize, outputFormat, task, sourceFile)).start();

				final int exitCode = process.waitFor();
				logger.debugf("WhisperX final status %d", exitCode);

				if (exitCode == 0) {
					if (Files.exists(Paths.get(resultFile))) {
						// Read result file
						logger.infof(
								"Finished whisperX call with task '%s' in language '%s' for input '%s' and output '%s' in the format '%s'",
								task, language, sourceFile, resultFile, outputFormat);
						return Files.readString(Paths.get(resultFile), StandardCharsets.UTF_8);
					} else {
						throw new IOException("Result file " + resultFile + " not found");
					}
				} else {
					throw new IOException("Failed to invoke " + executable + " with code " + exitCode);
				}
			} catch (IOException | InterruptedException e) {
				throw new RuntimeException("Failed to invoke " + executable, e);
			} finally {
				// Clean up both temporary files
				try {
					Files.deleteIfExists(Paths.get(sourceFile));
					Files.deleteIfExists(Paths.get(resultFile));
				} catch (IOException e) {
					throw new RuntimeException("Failed to clean up files", e);
				}
			}
		}, executor);
	}

	/**
	 * Builds the process invocation command to start whisperX
	 * 
	 * @param language     Language of the wave file
	 * @param outputFormat Output format of the text file
	 * @param task         Task to perform
	 * @param sourceFile   Name of the source file
	 * @param diarize      Perform speaker diarization
	 * @return Command Array
	 */
	private String[] buildProcessInvocation(String language, boolean diarize, String outputFormat, String task,
			final String sourceFile) {
		// Prepare default parameters
		final List<String> parameters = new ArrayList<>();
		parameters.addAll(Arrays.asList(executable, sourceFile, //
				"--compute_type", computeType, //
				"--output_dir", workdir, //
				"--task", task, //
				"--model", this.whisperXModel, //
				"--output_format", outputFormat //
		));
		// Add optional parameters
		if (language != null && !language.isBlank()) {
			parameters.add("--language");
			parameters.add(language);
		}

		if (this.whisperXThreads.isPresent()) {
			parameters.add("--threads");
			parameters.add(this.whisperXThreads.get().toString());
		}

		if (this.hfToken.isPresent() && !this.hfToken.get().isBlank()) {
			parameters.add("--hf_token");
			parameters.add(this.hfToken.get());
		}

		if ((diarize || this.diarize) && this.hfToken.isPresent()) {
			parameters.add("--diarize");
		}

		logger.debugf("Invoking %s", parameters.stream().collect(Collectors.joining(" "))
				.replace(this.hfToken.orElse("***"), "***"));
		return parameters.toArray(new String[parameters.size()]);
	}

}
