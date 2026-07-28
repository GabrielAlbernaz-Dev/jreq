package com.jreq.request.presentation;

import com.jreq.shared.ui.ResponsiveLayoutManager;
import com.jreq.shared.ui.components.EmptyStateView;
import com.jreq.shared.ui.components.RequestBarControl;
import com.jreq.shared.ui.components.ResponseMetadataView;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.util.Objects;

public final class MainController {
    private final MainViewModel viewModel;

    @FXML
    private BorderPane mainRoot;
    @FXML
    private VBox sidebar;
    @FXML
    private Button sidebarToggle;
    @FXML
    private RequestBarControl requestBar;
    @FXML
    private TabPane requestTabs;
    @FXML
    private TabPane responseTabs;
    @FXML
    private ResponseMetadataView responseMetadata;
    @FXML
    private TextArea responseBody;
    @FXML
    private Label statusMessage;
    @FXML
    private EmptyStateView authEmptyState;

    private ResponsiveLayoutManager responsiveLayoutManager;

    public MainController(MainViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    @FXML
    private void initialize() {
        requestBar.methodProperty().bindBidirectional(viewModel.selectedMethodProperty());
        requestBar.urlProperty().bindBidirectional(viewModel.urlProperty());
        requestBar.setOnSend(viewModel::sendRequest);
        viewModel.loadingProperty().addListener((observable, oldValue, loading) ->
                requestBar.setLoading(loading));

        sidebar.visibleProperty().bind(viewModel.sidebarExpandedProperty());
        sidebar.managedProperty().bind(viewModel.sidebarExpandedProperty());
        sidebarToggle.setOnAction(event -> viewModel.toggleSidebar());

        responseMetadata.statusProperty().bind(viewModel.responseStatusProperty());
        responseMetadata.durationProperty().bind(viewModel.responseDurationProperty());
        responseMetadata.sizeProperty().bind(viewModel.responseSizeProperty());
        responseBody.textProperty().bind(viewModel.responseBodyProperty());
        statusMessage.textProperty().bind(viewModel.statusMessageProperty());

        requestTabs.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) ->
                viewModel.selectedRequestTabProperty().set(tabText(newTab)));
        responseTabs.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) ->
                viewModel.selectedResponseTabProperty().set(tabText(newTab)));

        authEmptyState.setTitle("Authentication is not configured");
        authEmptyState.setDescription("Auth strategies will be added incrementally without storing credentials here.");
    }

    public void installSceneBehavior(Scene scene) {
        responsiveLayoutManager = new ResponsiveLayoutManager(mainRoot, viewModel.sidebarExpandedProperty());
        responsiveLayoutManager.modeProperty().addListener((observable, oldMode, newMode) ->
                viewModel.responsiveModeProperty().set(newMode));
        responsiveLayoutManager.attach(scene);

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN),
                viewModel::sendRequest
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN),
                viewModel::toggleSidebar
        );
    }

    @FXML
    private void handleNewRequest() {
        viewModel.newRequest();
        requestBar.requestUrlFocus();
    }

    private String tabText(Tab tab) {
        return tab == null ? "" : tab.getText();
    }
}
