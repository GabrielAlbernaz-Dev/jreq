package com.jreq.request.presentation;

import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestHistoryEntry;
import com.jreq.request.domain.RequestLocation;
import com.jreq.request.domain.SavedRequest;
import com.jreq.shared.ui.components.SidebarItemView;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

final class WorkspaceSidebar {
    private static final DateTimeFormatter HISTORY_TIME =
            DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

    private final VBox rootRequests;
    private final VBox collectionsList;
    private final VBox historyList;
    private final Actions actions;

    WorkspaceSidebar(VBox rootRequests, VBox collectionsList, VBox historyList, Actions actions) {
        this.rootRequests = Objects.requireNonNull(rootRequests, "rootRequests");
        this.collectionsList = Objects.requireNonNull(collectionsList, "collectionsList");
        this.historyList = Objects.requireNonNull(historyList, "historyList");
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    void render(
            List<RequestCollection> collections,
            List<SavedRequest> savedRequests,
            List<RequestHistoryEntry> history
    ) {
        rootRequests.getChildren().clear();
        collectionsList.getChildren().clear();
        historyList.getChildren().clear();

        List<SavedRequest> rootItems = savedRequests.stream()
                .filter(item -> item.location() instanceof RequestLocation.Root)
                .toList();
        addRequestItems(rootRequests, rootItems, "No individual requests");

        for (RequestCollection collection : collections) {
            collectionsList.getChildren().add(collectionGroup(collection, savedRequests));
        }
        if (collections.isEmpty()) {
            collectionsList.getChildren().add(emptyLabel("No collections yet"));
        }

        for (RequestHistoryEntry entry : history) {
            historyList.getChildren().add(historyItem(entry));
        }
        if (history.isEmpty()) {
            historyList.getChildren().add(emptyLabel("No history yet"));
        }
    }

    private Node collectionGroup(RequestCollection collection, List<SavedRequest> savedRequests) {
        VBox requests = new VBox();
        requests.getStyleClass().add("collection-requests");
        List<SavedRequest> items = savedRequests.stream()
                .filter(item -> item.location() instanceof RequestLocation.Collection location
                        && location.collectionId().equals(collection.id()))
                .toList();
        addRequestItems(requests, items, "Empty collection");

        Label disclosure = new Label("▾");
        disclosure.getStyleClass().add("collection-disclosure");
        Label name = new Label(collection.name());
        name.getStyleClass().add("collection-name");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label count = new Label(Integer.toString(items.size()));
        count.getStyleClass().add("collection-count");
        HBox heading = new HBox(disclosure, name, spacer, count);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.getStyleClass().add("collection-heading");
        installContextMenu(heading, collectionContextMenu(collection));
        heading.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                boolean expanded = requests.isManaged();
                requests.setManaged(!expanded);
                requests.setVisible(!expanded);
                disclosure.setText(expanded ? "›" : "▾");
            }
        });

        VBox group = new VBox(heading, requests);
        group.getStyleClass().add("collection-group");
        return group;
    }

    private void addRequestItems(VBox target, List<SavedRequest> requests, String emptyText) {
        if (requests.isEmpty()) {
            target.getChildren().add(emptyLabel(emptyText));
            return;
        }
        requests.forEach(saved -> target.getChildren().add(requestItem(saved)));
    }

    private Node requestItem(SavedRequest savedRequest) {
        SidebarItemView item = new SidebarItemView(
                savedRequest.definition().method().name(), savedRequest.definition().name());
        Tooltip.install(item, new Tooltip(savedRequest.definition().url()));
        installContextMenu(item, requestContextMenu(savedRequest));
        item.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                actions.openRequest(savedRequest);
            }
        });
        return item;
    }

    private Node historyItem(RequestHistoryEntry entry) {
        SidebarItemView item = new SidebarItemView(entry.request().method().name(), entry.name());
        Tooltip.install(item, new Tooltip(
                entry.request().url() + "\n" + HISTORY_TIME.format(entry.createdAt())));
        MenuItem open = menuItem("Open snapshot", () -> actions.openHistory(entry));
        MenuItem delete = menuItem("Delete", () -> actions.deleteHistory(entry));
        installContextMenu(item, new ContextMenu(open, new SeparatorMenuItem(), delete));
        item.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                actions.openHistory(entry);
            }
        });
        return item;
    }

    private ContextMenu collectionContextMenu(RequestCollection collection) {
        MenuItem rename = menuItem("Rename…", () -> actions.renameCollection(collection));
        MenuItem delete = menuItem("Delete…", () -> actions.deleteCollection(collection));
        return new ContextMenu(rename, new SeparatorMenuItem(), delete);
    }

    private ContextMenu requestContextMenu(SavedRequest savedRequest) {
        MenuItem open = menuItem("Open", () -> actions.openRequest(savedRequest));
        MenuItem rename = menuItem("Rename…", () -> actions.renameRequest(savedRequest));
        Menu move = new Menu("Move to");
        move.getItems().add(menuItem("Root", () ->
                actions.moveRequest(savedRequest, RequestLocation.root())));
        for (RequestCollection collection : actions.collections()) {
            move.getItems().add(menuItem(collection.name(), () -> actions.moveRequest(
                    savedRequest, RequestLocation.collection(collection.id()))));
        }
        MenuItem duplicate = menuItem("Duplicate…", () -> actions.duplicateRequest(savedRequest));
        MenuItem delete = menuItem("Delete", () -> actions.deleteRequest(savedRequest));
        return new ContextMenu(open, rename, move, duplicate, new SeparatorMenuItem(), delete);
    }

    private MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }

    private void installContextMenu(Node node, ContextMenu menu) {
        node.setOnContextMenuRequested(event -> {
            menu.show(node, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private Label emptyLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("sidebar-empty-label");
        return label;
    }

    interface Actions {
        List<RequestCollection> collections();

        void openRequest(SavedRequest request);

        void renameRequest(SavedRequest request);

        void moveRequest(SavedRequest request, RequestLocation location);

        void duplicateRequest(SavedRequest request);

        void deleteRequest(SavedRequest request);

        void renameCollection(RequestCollection collection);

        void deleteCollection(RequestCollection collection);

        void openHistory(RequestHistoryEntry entry);

        void deleteHistory(RequestHistoryEntry entry);
    }
}
