package com.phanduy.aliexscrap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.phanduy.aliexscrap.config.Configs;
import com.phanduy.aliexscrap.controller.DownloadManager;
import com.phanduy.aliexscrap.controller.SocketManager;
import com.phanduy.aliexscrap.controller.NetworkMonitor;
import com.phanduy.aliexscrap.controller.inputprocess.InputDataConfig;
import com.phanduy.aliexscrap.controller.inputprocess.SnakeReadOrderInfoSvs;
import com.phanduy.aliexscrap.controller.thread.CrawlExecutor;
import com.phanduy.aliexscrap.controller.thread.ExportFileExecutor;
import com.phanduy.aliexscrap.controller.thread.ProcessCrawlRapidNoCrawlThread;
import com.phanduy.aliexscrap.interfaces.CrawlProcessListener;
import com.phanduy.aliexscrap.interfaces.DownloadListener;
import com.phanduy.aliexscrap.model.ProductPage;
import com.phanduy.aliexscrap.model.aliex.store.inputdata.BaseStoreOrderInfo;
import com.phanduy.aliexscrap.model.request.CheckInfoReq;
import com.phanduy.aliexscrap.model.request.UpdateCrawlSignatureReq;
import com.phanduy.aliexscrap.model.response.CheckInfoResponse;
import com.phanduy.aliexscrap.model.response.ResponseObj;
import com.phanduy.aliexscrap.api.ApiCall;
import com.phanduy.aliexscrap.utils.*;
import com.phanduy.aliexscrap.utils.ComputerIdentifier;
import com.phanduy.aliexscrap.utils.ExcelUtils;
import com.phanduy.aliexscrap.utils.DataUtils;
import com.phanduy.aliexscrap.utils.StringUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Tooltip;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.prefs.Preferences;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.control.TableCell;
import javafx.util.Callback;
import javafx.scene.layout.Region;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.geometry.Pos;
import org.jetbrains.annotations.Nullable;

import static com.phanduy.aliexscrap.api.ApiClient.SOCKET_URL;

import java.net.URL;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.util.stream.Stream;

public class OldHomePanelController {
    @FXML private Label statusLabel;
    @FXML private Button browseProductFile;
    @FXML private TextField numOfProductField;
    @FXML private TextField productFileField;
    @FXML private Button startButton;

    private Preferences prefs;


    @FXML
    public void initialize() throws URISyntaxException {
        prefs = Preferences.userNodeForPackage(OldHomePanelController.class);
        productFileField.setText(prefs.get("productFileField", ""));
        numOfProductField.setText(prefs.get("numOfProductField", ""));
    }

    @FXML
    private void onStart() throws IOException {
        String numOfProductStr = numOfProductField.getText();
        int numOfProduct = 0;
        try {
            numOfProduct = Integer.parseInt(numOfProductStr);
        } catch (NumberFormatException ex) {
            AlertUtil.showError("Error", "Số lượng sản phẩm không hợp lệ");
            return;
        }

        if (numOfProduct <= 0) {
            AlertUtil.showError("Error", "Số lượng sản phẩm phải dương");
            return;
        }

        prefs.put("numOfProductField", "" + numOfProduct);

        String filePath = productFileField.getText();
        if (StringUtils.isEmpty(filePath)) {
            AlertUtil.showError("", "Product file not selected!");
            return;
        }

        File checkFile = new File(filePath);
        if (!checkFile.exists()) {
            AlertUtil.showError("", "Product file does not exist!");
            return;
        }

        final int count = numOfProduct;

        statusLabel.setText("Đang xử lý...");
        Thread thread = new Thread(
                () -> {
                    try {
                        String folderPath = filePath.substring(0, filePath.lastIndexOf("\\"));
                        SplitResult splitResult = ExcelReader.splitFile(filePath, count);
                        Platform.runLater(() -> {
                            statusLabel.setText("");
                            boolean result = AlertUtil.showAlert("Hoàn thành", "Đã tách xong!\n" + splitResult.totalProduct + " products\n" + splitResult.totalPage + " pages");
                            if (result) {
                                try {
                                    File folder = new File(folderPath);

                                    if (!Desktop.isDesktopSupported()) {
                                        System.out.println("Desktop không được hỗ trợ");
                                        return;
                                    }

                                    Desktop.getDesktop().open(folder);

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    } catch (IOException e) {
                        System.out.println(e);
                        Platform.runLater(() -> AlertUtil.showAlert("Lỗi", e.getMessage()));
                    }
                }
        );
        thread.start();
    }



    public void onBrowserProductFile() {
        String currentPath = productFileField.getText();
        String folderPath = null;
        if (currentPath.isEmpty()) {
            folderPath = ".";
        } else {
            folderPath = currentPath.substring(0, currentPath.lastIndexOf("\\"));
        }
        FileChooser fileChooser = new FileChooser();
        try {
            File init = getFallbackInitialDirectory(folderPath);
            fileChooser.setInitialDirectory(init);
        } catch (Exception ignore) { }

        // Set the title for the FileChooser dialog
        fileChooser.setTitle("Select Excel File");

        // Restrict the selection to Excel files
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xls", "*.xlsx")
        );

        // Show the dialog and get the selected file
        Stage stage = (Stage) browseProductFile.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        // Process the selected file
        if (selectedFile != null) {
            productFileField.setText(selectedFile.getAbsolutePath());
            prefs.put("productFileField", selectedFile.getAbsolutePath());
        }
    }

    private File getFallbackInitialDirectory(String preferredPath) {
        ArrayList<String> candidates = new ArrayList<>();
        if (preferredPath != null && !preferredPath.isEmpty()) {
            candidates.add(preferredPath);
        }
        String userHome = System.getProperty("user.home");
        candidates.add(userHome + File.separator + "Desktop");
        candidates.add(userHome + File.separator + "Documents");
        candidates.add(userHome);
        for (String p : candidates) {
            try {
                if (p == null) continue;
                File f = new File(p);
                if (f.exists() && f.isDirectory() && f.canRead()) {
                    return f;
                }
            } catch (SecurityException ignore) { }
        }
        return new File(userHome);
    }
}
