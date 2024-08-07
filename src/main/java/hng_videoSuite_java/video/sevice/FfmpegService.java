package hng_videoSuite_java.video.sevice;

import hng_videoSuite_java.notification.VideoPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FfmpegService {
    private final VideoPublisherService videoPublisherService;
    private final FfmpegUtils ffmpegUtils;
    private final String ffmpeg = "ffmpeg";
    private final String ffprobe = "ffprobe";

    public void encodeVideo(String inputFilePath, String outputFilePath,
                             double totalDuration, UUID jobId) throws IOException, ExecutionException, InterruptedException {
        log.info("Encoding video: {}", inputFilePath);
        String command = String.format("%s -i %s -c:v libx264 -c:a aac -strict experimental %s",
                ffmpeg, inputFilePath, outputFilePath);
        Process process = ffmpegUtils.executeCommand(command);
        ffmpegUtils.handleProcessOutput(process, totalDuration, jobId);
    }


    public void mergeVideos(String outputFilePath, UUID jobId,
                            String... inputFiles) throws IOException, InterruptedException, ExecutionException {
        double totalDuration = 0;

        log.info("Starting video merge process for output file: {}", outputFilePath);

        for (String inputFile: inputFiles) {
            totalDuration += ffmpegUtils.getVideoDuration(inputFile, ffprobe);
        }

        // Re-encode all videos to ensure same format
        Path tempDir = Files.createTempDirectory("merge_videos");
        String[] encodedFiles = new String[inputFiles.length];
        for (int i = 0; i < inputFiles.length; i++) {
            Path encodedFilePath = tempDir.resolve("re-encoded_" + Paths.get(inputFiles[i]).getFileName());
            encodeVideo(inputFiles[i], encodedFilePath.toString(), totalDuration, jobId);
            encodedFiles[i] = encodedFilePath.toString();
        }

        // create a list file for the concatenation
        Path listFile = tempDir.resolve("input_files.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(listFile)) {
            for (String encodedFile : encodedFiles) {
                writer.write("file '" + encodedFile + "'\n");
            }
        }

        // merge the re-encoded videos
        String command = String.format("%s -f concat -safe 0 -i %s -c copy %s",
                ffmpeg, listFile.toAbsolutePath(), outputFilePath);
        Process process = ffmpegUtils.executeCommand(command);
        ffmpegUtils.handleProcessOutput(process, totalDuration, jobId);

        // clean up tmp files
        Files.deleteIfExists(listFile);
        for (String encodedFile : encodedFiles) {
            Files.deleteIfExists(Paths.get(encodedFile));
        }
        Files.deleteIfExists(tempDir);

        try {
            videoPublisherService.publishMergedVideo(jobId, new File(outputFilePath));
        } catch (IOException ex) {
            log.error("Failed to publish merged video: {}", ex.getMessage());
        }
    }
}
