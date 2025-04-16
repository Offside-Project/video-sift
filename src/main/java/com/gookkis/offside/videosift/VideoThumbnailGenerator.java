package com.gookkis.offside.videosift;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import java.io.File;

public class VideoThumbnailGenerator {

    public static Image generateThumbnail(File videoFile) {
        try {
            // Load the video file
            String videoPath = videoFile.toURI().toString();
            Media media = new Media(videoPath);
            MediaPlayer mediaPlayer = new MediaPlayer(media);

            // Create a MediaView to capture the snapshot
            MediaView mediaView = new MediaView(mediaPlayer);

            // Wait until the media is ready
            mediaPlayer.setOnReady(() -> {
                // Seek to 1 second (or any desired time)
                mediaPlayer.seek(Duration.seconds(1));
            });

            // Capture the snapshot
            WritableImage snapshot = mediaView.snapshot(null, null);

            // Dispose of the MediaPlayer after use
            mediaPlayer.dispose();

            return snapshot;
        } catch (Exception e) {
            System.err.println("Failed to generate thumbnail: " + e.getMessage());
            e.printStackTrace(System.err);
            return null;
        }
    }
}