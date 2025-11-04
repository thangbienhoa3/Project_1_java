module org.example.gui {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires java.desktop;
    requires org.bytedeco.opencv;
    opens org.example.gui.controller to javafx.fxml;
    opens org.example.gui to javafx.fxml;
    exports org.example.gui;
}
