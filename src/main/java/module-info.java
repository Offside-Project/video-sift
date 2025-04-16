module com.gookkis.offside.videosift {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires javafx.swing;
    requires jdk.compiler;
    requires transitive javafx.graphics;

    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.annotation;

    opens com.gookkis.offside.videosift to javafx.fxml;
    exports com.gookkis.offside.videosift;
}