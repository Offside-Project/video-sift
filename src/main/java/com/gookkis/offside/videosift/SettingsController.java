package com.gookkis.offside.videosift;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.beans.property.SimpleStringProperty;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SettingsController {

    @FXML
    private TableView<KeyDirectoryMapping> settingsTable;

    private final ObservableList<KeyDirectoryMapping> tableData = FXCollections.observableArrayList();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path settingsFile = Path.of("settings.json");

    @FXML
    public void initialize() {
        // Load settings from JSON file
        loadSettings();

        // Populate the table with all letter and number keys
        for (char key = 'A'; key <= 'Z'; key++) {
            final char currentKey = key;
            if (tableData.stream().noneMatch(mapping -> mapping.getKey().equals(String.valueOf(currentKey)))) {
                tableData.add(new KeyDirectoryMapping(String.valueOf(currentKey), ""));
            }
        }
        for (char key = '0'; key <= '9'; key++) {
            final char currentKey = key;
            if (tableData.stream().noneMatch(mapping -> mapping.getKey().equals(String.valueOf(currentKey)))) {
                tableData.add(new KeyDirectoryMapping(String.valueOf(currentKey), ""));
            }
        }

        settingsTable.setItems(tableData);

        // Set up columns
        TableColumn<KeyDirectoryMapping, String> keyColumn = new TableColumn<>("Key");
        keyColumn.setCellValueFactory(cellData -> cellData.getValue().keyProperty());

        TableColumn<KeyDirectoryMapping, String> directoryColumn = new TableColumn<>("Target Directory");
        directoryColumn.setCellValueFactory(cellData -> cellData.getValue().directoryProperty());
        directoryColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        directoryColumn.setOnEditCommit(event -> {
            KeyDirectoryMapping mapping = event.getRowValue();
            mapping.setDirectory(event.getNewValue());
        });

        TableColumn<KeyDirectoryMapping, Void> actionColumn = new TableColumn<>("Action");
        actionColumn.setCellFactory(param -> new TableCell<>() {
            private final Button button = new Button("Set Directory");

            {
                button.setOnAction(event -> {
                    KeyDirectoryMapping mapping = getTableView().getItems().get(getIndex());
                    browseDirectory(mapping);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(button);
                }
            }
        });

        settingsTable.getColumns().clear();
        Collections.addAll(settingsTable.getColumns(), keyColumn, directoryColumn, actionColumn);
    }

    private void browseDirectory(KeyDirectoryMapping mapping) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Directory for Key: " + mapping.getKey());
        File selectedDirectory = directoryChooser.showDialog(null);

        if (selectedDirectory != null) {
            mapping.setDirectory(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void saveSettings() {
        // Save the settings to a JSON file
        Map<String, String> settingsMap = new HashMap<>();
        tableData.forEach(mapping -> settingsMap.put(mapping.getKey(), mapping.getDirectory()));

        try {
            objectMapper.writeValue(settingsFile.toFile(), settingsMap);
            System.out.println("Settings saved successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Close the settings window
        Stage stage = (Stage) settingsTable.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void cancelSettings() {
        // Close the settings window without saving
        Stage stage = (Stage) settingsTable.getScene().getWindow();
        stage.close();
    }

    private void loadSettings() {
        if (Files.exists(settingsFile)) {
            try {
                Map<String, String> settingsMap = objectMapper.readValue(settingsFile.toFile(), new TypeReference<>() {});
                settingsMap.forEach((key, directory) -> {
                    if (key != null && directory != null) {
                        tableData.add(new KeyDirectoryMapping(key, directory));
                    }
                });
            } catch (IOException e) {
                System.err.println("Failed to load settings: " + e.getMessage());
            }
        }
    }

    public static class KeyDirectoryMapping {
        private final SimpleStringProperty key;
        private final SimpleStringProperty directory;

        public KeyDirectoryMapping(String key, String directory) {
            this.key = new SimpleStringProperty(key);
            this.directory = new SimpleStringProperty(directory);
        }

        public String getKey() {
            return key.get();
        }

        public SimpleStringProperty keyProperty() {
            return key;
        }

        public String getDirectory() {
            return directory.get();
        }

        public SimpleStringProperty directoryProperty() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory.set(directory);
        }
    }
}
