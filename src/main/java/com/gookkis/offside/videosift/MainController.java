package com.gookkis.offside.videosift;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.awt.Desktop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MainController {

    @FXML
    private Button btnPlay;

    @FXML
    private Label lblDuration;

    @FXML
    private Label lblFileName;

    @FXML
    private MediaView mediaView;

    @FXML
    private Slider slider;

    @FXML
    private ListView<String> videoListView;

    private Media media;
    private MediaPlayer mediaPlayer;

    private boolean isPlayed = false;

    private ObservableList<File> videoList = FXCollections.observableArrayList();
    private ObservableList<String> videoNamesList = FXCollections.observableArrayList();
    private int currentVideoIndex = -1;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, String> keyDirectoryMap = new HashMap<>();

    @FXML
    public void initialize() {

        // Set up the event handler for the ListView
        videoListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) { // Double click
                int selectedIndex = videoListView.getSelectionModel().getSelectedIndex();
                if (selectedIndex >= 0) {
                    currentVideoIndex = selectedIndex;
                    playVideo(videoList.get(currentVideoIndex));
                }
            }
        });

        // Add a listener to ensure the Scene is available before adding the event
        // filter
        videoListView.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    loadSettings();
                    String keyPressed = event.getCode().getName().toUpperCase();
                    if (keyDirectoryMap.containsKey(keyPressed)) {
                        System.out.println("Key pressed: " + keyPressed);
                        System.out.println("Target directory: " + keyDirectoryMap.get(keyPressed));
                        moveCurrentFileToTargetDirectory(keyPressed);
                    } else if (event.getCode() == KeyCode.RIGHT) { // Shortcut for next video
                        btnNext(null);
                    } else if (event.getCode() == KeyCode.LEFT) { // Shortcut for previous video
                        btnPrevious(null);
                    } else if (event.getCode() == KeyCode.DELETE) { // Handle DELETE key
                        moveToRecycleBin();
                    }
                });
            }
        });
    }

    private void loadSettings() {
        try {
            File settingsFile = new File("settings.json");
            if (settingsFile.exists()) {
                keyDirectoryMap = objectMapper.readValue(settingsFile, new TypeReference<>() {
                });
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    private void moveCurrentFileToTargetDirectory(String keyPressed) {
        if (currentVideoIndex >= 0 && currentVideoIndex < videoList.size()) {
            File currentFile = videoList.get(currentVideoIndex);

            // Check if the file exists and is not locked
            if (currentFile == null || !currentFile.exists() || !currentFile.canWrite()) {
                System.out.println("File does not exist or is locked: " + currentFile);
                return;
            }

            String targetDirectoryPath = keyDirectoryMap.get(keyPressed);

            if (targetDirectoryPath != null) {
                File targetDirectory = new File(targetDirectoryPath);
                if (targetDirectory.isDirectory()) {
                    try {
                        // Dispose of the MediaPlayer to release the file
                        if (mediaPlayer != null) {
                            mediaPlayer.stop();
                            mediaPlayer.dispose();
                            mediaPlayer = null;
                        }

                        Path sourcePath = currentFile.toPath();
                        Path targetPath = targetDirectory.toPath().resolve(currentFile.getName());

                        // If target file exists, append a number to the filename
                        int count = 1;
                        while (Files.exists(targetPath)) {
                            String newFileName = currentFile.getName().replaceFirst("(\\.[^.]+)$", "_" + count + "$1");
                            targetPath = targetDirectory.toPath().resolve(newFileName);
                            count++;
                        }

                        // Retry mechanism for file lock
                        int retries = 5;
                        while (retries > 0) {
                            try {
                                // Attempt to move the file
                                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                                break; // Exit loop if successful
                            } catch (java.nio.file.FileSystemException e) {
                                retries--;
                                if (retries == 0) {
                                    throw e; // Rethrow exception if retries are exhausted
                                }
                                System.out.println("File is locked, retrying... (" + retries + " retries left)");
                                Thread.sleep(500); // Wait for 500ms before retrying
                            }
                        }

                        videoList.remove(currentVideoIndex);
                        videoNamesList.remove(currentVideoIndex);

                        if (videoList.isEmpty()) {
                            mediaView.setMediaPlayer(null);
                            lblDuration.setText("Remaining: 00:00:00");
                            lblFileName.setText("Current File: None");
                            btnPlay.setText("Play");
                            isPlayed = false;
                            currentVideoIndex = -1;
                        } else {
                            if (currentVideoIndex >= videoList.size()) {
                                currentVideoIndex = videoList.size() - 1;
                            }
                            playVideo(videoList.get(currentVideoIndex));
                            videoListView.getSelectionModel().select(currentVideoIndex);
                        }
                    } catch (Exception e) {
                        System.err.println("Error: " + e.getMessage());
                        e.printStackTrace(System.err);
                        System.out.println("Failed to move file to: " + targetDirectoryPath);
                    }
                }
            }
        }
    }

    private void moveToRecycleBin() {
        if (currentVideoIndex >= 0 && currentVideoIndex < videoList.size()) {
            File currentFile = videoList.get(currentVideoIndex);

            // Check if the file exists
            if (currentFile == null || !currentFile.exists()) {
                System.out.println("File does not exist: " + currentFile);
                return;
            }

            try {
                // Attempt to move the file to the recycle bin using java.nio.file.Files
                Path filePath = currentFile.toPath();
                Files.delete(filePath); // Deletes the file (simulate recycle bin behavior)

                videoList.remove(currentVideoIndex);
                videoNamesList.remove(currentVideoIndex);

                if (videoList.isEmpty()) {
                    if (mediaPlayer != null) {
                        mediaPlayer.stop();
                    }
                    mediaView.setMediaPlayer(null);
                    lblDuration.setText("Remaining: 00:00:00");
                    lblFileName.setText("Current File: None");
                    btnPlay.setText("Play");
                    isPlayed = false;
                    currentVideoIndex = -1;
                } else {
                    if (currentVideoIndex >= videoList.size()) {
                        currentVideoIndex = videoList.size() - 1;
                    }
                    playVideo(videoList.get(currentVideoIndex));
                    videoListView.getSelectionModel().select(currentVideoIndex);
                }
            } catch (Exception e) {
                System.out.println("Failed to move file to Recycle Bin using Files API. Falling back to Desktop API.");
                try {
                    boolean isDeleted = Desktop.getDesktop().moveToTrash(currentFile); // Fallback to Desktop API
                    if (!isDeleted) {
                        System.out.println("Failed to move file to Recycle Bin using Desktop API.");
                        return;
                    }
                    videoList.remove(currentVideoIndex);
                    videoNamesList.remove(currentVideoIndex);

                    if (videoList.isEmpty()) {
                        if (mediaPlayer != null) {
                            mediaPlayer.stop();
                        }
                        mediaView.setMediaPlayer(null);
                        lblDuration.setText("Remaining: 00:00:00");
                        lblFileName.setText("Current File: None");
                        btnPlay.setText("Play");
                        isPlayed = false;
                        currentVideoIndex = -1;
                    } else {
                        if (currentVideoIndex >= videoList.size()) {
                            currentVideoIndex = videoList.size() - 1;
                        }
                        playVideo(videoList.get(currentVideoIndex));
                        videoListView.getSelectionModel().select(currentVideoIndex);
                    }
                } catch (Exception fallbackException) {
                    System.err.println("Error: " + fallbackException.getMessage());
                    fallbackException.printStackTrace(System.err);
                    System.out.println("Failed to move file to Recycle Bin.");
                }
            }
        }
    }

    /**
     * Format seconds to hh:mm:ss format
     * 
     * @param seconds The time in seconds
     * @return Formatted time string
     */
    private String formatTime(double seconds) {
        int hours = (int) seconds / 3600;
        int minutes = ((int) seconds % 3600) / 60;
        int secs = (int) seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    /**
     * Calculate remaining time based on current position and total duration
     *
     * @param currentSeconds Current position in seconds
     * @param totalSeconds   Total duration in seconds
     * @return Remaining time in seconds
     */
    private double calculateRemainingTime(double currentSeconds, double totalSeconds) {
        return Math.max(0, totalSeconds - currentSeconds);
    }

    @FXML
    void btnPlay(MouseEvent event) {
        if (!isPlayed) {
            btnPlay.setText("Pause");
            mediaPlayer.play();
            isPlayed = true;
        } else {
            btnPlay.setText("Play");
            mediaPlayer.pause();
            isPlayed = false;
        }
    }

    @FXML
    void btnStop(MouseEvent event) {
        btnPlay.setText("Play");
        mediaPlayer.stop();
        isPlayed = false;
    }

    @FXML
    void selectMedia(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Media");
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null) {
            String url = selectedFile.toURI().toString();

            media = new Media(url);
            mediaPlayer = new MediaPlayer(media);

            mediaView.setMediaPlayer(mediaPlayer);

            mediaPlayer.currentTimeProperty().addListener(((observableValue, oldValue, newValue) -> {
                slider.setValue(newValue.toSeconds());
                double currentSeconds = newValue.toSeconds();
                double totalSeconds = media.getDuration().toSeconds();
                double remainingSeconds = calculateRemainingTime(currentSeconds, totalSeconds);
                lblDuration.setText("Remaining: " + formatTime(remainingSeconds));
            }));

            mediaPlayer.setOnReady(() -> {
                Duration totalDuration = media.getDuration();
                slider.setMax(totalDuration.toSeconds());
                lblDuration.setText("Remaining: " + formatTime(totalDuration.toSeconds()));
            });

            Scene scene = mediaView.getScene();
            mediaView.fitWidthProperty().bind(scene.widthProperty());
            mediaView.fitHeightProperty().bind(scene.heightProperty());
        }
    }

    @FXML
    private void sliderPressed(MouseEvent event) {
        mediaPlayer.seek(Duration.seconds(slider.getValue()));
    }

    @FXML
    private void openFolder(MouseEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Folder");
        File selectedDirectory = directoryChooser.showDialog(null);

        if (selectedDirectory != null) {
            videoList.clear();
            videoNamesList.clear();
            addVideosFromDirectory(selectedDirectory);

            // Update the ListView
            videoListView.setItems(videoNamesList);

            if (!videoList.isEmpty()) {
                currentVideoIndex = 0;
                playVideo(videoList.get(currentVideoIndex));
                videoListView.getSelectionModel().select(0);
            }
        }
    }

    private void addVideosFromDirectory(File directory) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                addVideosFromDirectory(file); // Recursive call for subdirectories
            } else if (file.isFile() && file.getName().matches(".*\\.(mp4|mkv|avi)$")) {
                videoList.add(file);
                videoNamesList.add(file.getName());
            }
        }
    }

    private void playVideo(File file) {
        if (file != null) {
            String url = file.toURI().toString();

            // Update the current file name label
            lblFileName.setText("Current File: " + file.getName());

            if (mediaPlayer != null) {
                mediaPlayer.dispose();
            }

            media = new Media(url);
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);

            mediaPlayer.currentTimeProperty().addListener((observableValue, oldValue, newValue) -> {
                slider.setValue(newValue.toSeconds());
                double currentSeconds = newValue.toSeconds();
                double totalSeconds = media.getDuration().toSeconds();
                double remainingSeconds = calculateRemainingTime(currentSeconds, totalSeconds);
                lblDuration.setText("Remaining: " + formatTime(remainingSeconds));
            });

            mediaPlayer.setOnReady(() -> {
                Duration totalDuration = media.getDuration();
                slider.setMax(totalDuration.toSeconds());
                lblDuration.setText("Remaining: " + formatTime(totalDuration.toSeconds()));
            });

            mediaPlayer.play();
            btnPlay.setText("Pause");
            isPlayed = true;
        }
    }

    @FXML
    private void btnNext(MouseEvent event) {
        if (currentVideoIndex < videoList.size() - 1) {
            currentVideoIndex++;
            playVideo(videoList.get(currentVideoIndex));
            videoListView.getSelectionModel().select(currentVideoIndex);
        }
    }

    @FXML
    private void btnPrevious(MouseEvent event) {
        if (currentVideoIndex > 0) {
            currentVideoIndex--;
            playVideo(videoList.get(currentVideoIndex));
            videoListView.getSelectionModel().select(currentVideoIndex);
        }
    }

    @FXML
    private void btnSettings(MouseEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("settings-view.fxml"));
            Parent root = loader.load();

            Stage settingsStage = new Stage();
            settingsStage.initModality(Modality.APPLICATION_MODAL);
            settingsStage.setTitle("Settings");
            settingsStage.setScene(new Scene(root));
            settingsStage.showAndWait();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    @FXML
    private void openSettings(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("settings-view.fxml"));
            Parent root = loader.load();

            Stage settingsStage = new Stage();
            settingsStage.initModality(Modality.APPLICATION_MODAL);
            settingsStage.setTitle("Settings");
            settingsStage.setScene(new Scene(root));
            settingsStage.showAndWait();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}
