package hng_videoSuite_java.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import hng_videoSuite_java.video.dto.VideoPathDto;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VideoPublisherService {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rabbitmq.queue.finishedConcat:finishedConcat}")
    private String queueName;

    public void publishMergedVideo(String jobId, File videoFile) throws IOException {
        byte[] videoData = Files.readAllBytes(videoFile.toPath());
        Map<String, byte[]> mergedVideo = new HashMap<>();
        mergedVideo.put("merged_video.mp4", videoData);

        VideoPathDto videoPathDto = new VideoPathDto();
        videoPathDto.setJobId(jobId);
        videoPathDto.setVideo(mergedVideo);

        String message = objectMapper.writeValueAsString(videoPathDto);

        rabbitTemplate.convertAndSend(queueName, message);
    }
}
