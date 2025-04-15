module com.gookkis.offside.videosift {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires jdk.compiler;


    opens com.gookkis.offside.videosift to javafx.fxml;
    exports com.gookkis.offside.videosift;
}