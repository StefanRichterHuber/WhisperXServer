# WhisperX server

This project uses Quarkus, the Supersonic Subatomic Java Framework to build a webserver to invoke [WhisperX](https://github.com/m-bain/whisperX).

## Concept

[WhisperX](https://github.com/m-bain/whisperX) is a powerful tool for audio file transcription. Its an python cli project with gigabytes of dependencies, therefore I wanted to wrap it up in a more convenient way.
I could have used python to build the webserver, but I prefer Java, especially Quarkus since it easy to build a [12-factoy app](https://12factor.net/) with only a few lines of code. Additionally the ability to build native executables using GraalVM allows for docker images without further dependencies.

Since the transcription can take a very long time, the http request is not kept open (which will lead to timeouts eventually), but immediately accepted with status `202 accepted` and a unique job url is returned. This url can be polled until the transcription job is finished.

## How to build and run

There is a top-level Dockerfile which cares for both building the Java webserver (using Quarkus native) and WhisperX. So just clone the sources and build the image. To start the server simply use the docker image build and expose port `8080`.

```bash
git clone xxx
docker build -t whisperx-server:latest . 
docker run -v ./models:/root/.cache -p 8080:8080 whisperx-server:latest
```

WhisperX automatically downloads all required models and caches them on `/root/.cache` (therefore it should be mounted as volume to avoid downloading the same files over and over again). To enable diarization using [pyannote-audio](https://github.com/pyannote/pyannote-audio) you need to accept the terms of services of the model and add a hugging faces token.

```bash
docker run -v ./models:/root/.cache -e WHISPERX_HF_TOKEN=hf_XYZ -p 8080:8080 whisperx-server:latest
```

Further environment variables to configure the server:

| Name | Description | Default |
| ---      |  ----       | --- |
| `WHISPERX_MODEL` | Model to use for transciption. | `small` |
| `WHISPERX_PARALLEL_INSTANCES` | Number of parallel invocations of WhisperX. Further requests are queued | `1` |
| `WHISPERX_HF_TOKEN` | Hugging faces token to use [pyannote-audio](https://github.com/pyannote/pyannote-audio). Only necessary if diarization is requested. | - |
| `WHISPERX_THREADS` | Number of threads to use per invocation of WhisperX. Use it to limit cpu load on server | - (all threads are used) |

## How to use

### Transcription

Get a wav file in the necessary format (see [WhisperX](https://github.com/m-bain/whisperX)) and send it to the `transcribe` endpoint.

```bash
curl --location 'http://localhost:8080/transcribe?language=de&diarize=true' \
--header 'Content-Type: audio/wav' \
--header 'Accept: application/json' \
--data 'podcast.wav'
```

Header `Content-Type` is required to be `audio/wav`.

The `Accept` header of the request determines the format of the returned document:

- `application/json` -> json
- `text/plain` -> txt
- `text/src` -> srt
- `text/vtt` -> vtt
- `text/tsv` -> tsv

Optional query parameters:

- `language`: If not present, it will be automatically detected.
- `diarize`: Enable speaker diarization (takes far longer and requires the huggingfaces token!)

Since  the transcription can take a very long time and only one transcription is executed in parallel, the connection is not kept open until the result is present, but the request is immediately accepted with status  `202 Accepted` and a link to the current status of the transcription is returned:

```JSON
{
    "task": {
        "href": "/transcription-status?job-id=[UNIQUE_JOB_ID]",
        "id": "[UNIQUE_JOB_ID]"
    }
}
```

Poll this link until the final result file is returned.

```bash
curl --location 'http://localhost:8080/transcription-status?job-id=[UNIQUE_JOB_ID]'
```

### Conversion of audio files

In order to simplify the audio file conversion to the format necessary for transcription, a small helper endpoint is included (just calling `ffmpeg`). It will return the audio file in the required wav format.

```bash
curl --location 'http://localhost:8080/convert' \
--header 'Content-Type: audio/mpeg' \
--data '@podcast.mpga'
```

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```bash
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.
