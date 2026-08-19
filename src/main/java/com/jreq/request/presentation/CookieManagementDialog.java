package com.jreq.request.presentation;

import com.jreq.request.application.CookieJarEdit;
import com.jreq.request.domain.CookieExpiration;
import com.jreq.request.domain.StoredCookie;
import com.jreq.request.infrastructure.http.CookieHeaderParser;
import com.jreq.shared.ui.DialogButtons;
import com.jreq.shared.ui.ResponsiveLayoutMode;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CookieManagementDialog {
    private static final String CURRENT_URL = "Current URL";
    private static final String PASTE_PROMPT = """
            Paste Set-Cookie lines or a Cookie header.

            Examples:
            session=abc; Path=/; Secure; HttpOnly; Expires=Wed, 18 Aug 2027 12:00:00 GMT
            theme=dark; Path=/; Max-Age=3600
            session=abc; theme=dark
            """.strip();

    private final Window owner;
    private final URI currentUri;
    private final ResponsiveLayoutMode layoutMode;
    private final CookieManagerModel model;
    private final CookieHeaderParser parser = new CookieHeaderParser();
    private final ListView<String> scopes = new ListView<>();
    private final TableView<StoredCookie> table = new TableView<>();

    public CookieManagementDialog(
            Window owner,
            List<StoredCookie> cookies,
            URI currentUri,
            ResponsiveLayoutMode layoutMode
    ) {
        this.owner = owner;
        this.currentUri = Objects.requireNonNull(currentUri, "currentUri");
        this.layoutMode = Objects.requireNonNull(layoutMode, "layoutMode");
        this.model = new CookieManagerModel(cookies, currentUri, Instant.now());
    }

    public Optional<CookieJarEdit> show() {
        Dialog<CookieJarEdit> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("jREQ — Manage cookies");
        dialog.setHeaderText("Cookie jar");
        dialog.getDialogPane().getStyleClass().add("cookie-dialog");
        dialog.getDialogPane().setContent(content());
        ButtonType save = DialogButtons.save();
        dialog.getDialogPane().getButtonTypes().addAll(save, DialogButtons.cancel());
        dialog.setResultConverter(button -> button.equals(save) ? model.toEdit() : null);
        style(dialog);
        configureSize(dialog);
        refreshScopes();
        scopes.getSelectionModel().selectFirst();
        return dialog.showAndWait();
    }

    private Node content() {
        scopes.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
            }
        });
        scopes.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> refreshTable(selected));

        Button clearDomain = new Button("Clear domain");
        clearDomain.getStyleClass().add("quiet-action");
        clearDomain.setOnAction(event -> {
            String selected = scopes.getSelectionModel().getSelectedItem();
            if (selected != null && !CURRENT_URL.equals(selected)) {
                model.clearDomain(selected);
                refreshScopes();
            }
        });
        VBox navigation = new VBox(8, new Label("DOMAINS"), scopes, clearDomain);
        navigation.getStyleClass().add("cookie-navigation");
        VBox.setVgrow(scopes, Priority.ALWAYS);

        configureTable();
        Button add = new Button("+ Add cookie");
        add.getStyleClass().add("secondary-button");
        add.setOnAction(event -> editCookie(null).ifPresent(cookie -> {
            model.upsert(cookie);
            refreshScopes();
            selectDomain(cookie.domain());
        }));
        Button paste = new Button("Paste cookies…");
        paste.getStyleClass().add("secondary-button");
        paste.setOnAction(event -> pasteCookies());
        Button edit = new Button("Edit");
        edit.getStyleClass().add("secondary-button");
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        edit.setOnAction(event -> editCookie(table.getSelectionModel().getSelectedItem())
                .ifPresent(cookie -> {
                    model.upsert(cookie);
                    refreshScopes();
                    selectDomain(cookie.domain());
                }));
        Button delete = new Button("Delete");
        delete.getStyleClass().add("quiet-action");
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.setOnAction(event -> {
            StoredCookie selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                model.remove(selected.id());
                refreshScopes();
            }
        });
        Button clearAll = new Button("Clear all");
        clearAll.getStyleClass().add("quiet-action");
        clearAll.setOnAction(event -> {
            model.clearAll();
            refreshScopes();
        });
        HBox actions = new HBox(8, add, paste, edit, delete, clearAll);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox editor = new VBox(9,
                new Label("Cookies are stored locally. Values are masked until edited."),
                table,
                actions);
        editor.getStyleClass().add("cookie-editor");
        VBox.setVgrow(table, Priority.ALWAYS);

        SplitPane split = new SplitPane(navigation, editor);
        split.setOrientation(layoutMode == ResponsiveLayoutMode.COMPACT
                ? Orientation.VERTICAL : Orientation.HORIZONTAL);
        split.setDividerPositions(layoutMode == ResponsiveLayoutMode.COMPACT ? 0.28 : 0.22);
        split.getStyleClass().add("cookie-split");
        return split;
    }

    private void configureTable() {
        TableColumn<StoredCookie, String> name = column("Name", StoredCookie::name, 130);
        TableColumn<StoredCookie, String> value = column("Value", model::maskedValue, 150);
        TableColumn<StoredCookie, String> domain = column("Domain", StoredCookie::domain, 160);
        TableColumn<StoredCookie, String> path = column("Path", StoredCookie::path, 90);
        TableColumn<StoredCookie, String> expires = column("Expires", cookie ->
                cookie.expiration() instanceof CookieExpiration.At at ? at.instant().toString() : "Session", 175);
        TableColumn<StoredCookie, String> flags = column("Flags", cookie -> {
            String secure = cookie.secure() ? "Secure" : "";
            String httpOnly = cookie.httpOnly() ? "HttpOnly" : "";
            return String.join(" ", List.of(secure, httpOnly).stream().filter(text -> !text.isEmpty()).toList());
        }, 110);
        table.getColumns().setAll(List.of(name, value, domain, path, expires, flags));
        table.setPlaceholder(new Label("No cookies in this scope."));
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                editCookie(table.getSelectionModel().getSelectedItem()).ifPresent(cookie -> {
                    model.upsert(cookie);
                    refreshScopes();
                    selectDomain(cookie.domain());
                });
            }
        });
    }

    private TableColumn<StoredCookie, String> column(
            String title,
            java.util.function.Function<StoredCookie, String> value,
            double width
    ) {
        TableColumn<StoredCookie, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private void pasteCookies() {
        Dialog<List<StoredCookie>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("jREQ — Paste cookies");
        dialog.setHeaderText("Paste cookies as text");

        TextArea input = new TextArea();
        input.setPromptText(PASTE_PROMPT);
        input.setWrapText(true);
        input.setPrefRowCount(12);
        Label summary = new Label("Paste Set-Cookie lines or a Cookie header for the current URL host.");
        summary.setWrapText(true);
        Label validation = new Label();
        validation.getStyleClass().add("validation-message");
        validation.setWrapText(true);

        VBox body = new VBox(10, summary, input, validation);
        VBox.setVgrow(input, Priority.ALWAYS);
        dialog.getDialogPane().setContent(body);
        ButtonType importButton = new ButtonType("Import", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(importButton, DialogButtons.cancel());
        Node importNode = dialog.getDialogPane().lookupButton(importButton);
        importNode.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                buildPastedCookies(input.getText());
            } catch (IllegalArgumentException invalid) {
                validation.setText(invalid.getMessage());
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button.equals(importButton)
                ? buildPastedCookies(input.getText())
                : null);
        style(dialog);
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(
                layoutMode == ResponsiveLayoutMode.COMPACT ? 560 : 680,
                layoutMode == ResponsiveLayoutMode.COMPACT ? 420 : 460);
        dialog.showAndWait().ifPresent(cookies -> {
            String lastDomain = null;
            for (StoredCookie cookie : cookies) {
                model.upsert(cookie);
                lastDomain = cookie.domain();
            }
            refreshScopes();
            if (lastDomain != null) {
                selectDomain(lastDomain);
            }
        });
    }

    private List<StoredCookie> buildPastedCookies(String text) {
        Instant now = Instant.now();
        CookieHeaderParser.ParseReport report = parser.parsePaste(text == null ? "" : text, currentUri, now);
        if (report.accepted().isEmpty()) {
            String reason = report.rejected().isEmpty()
                    ? "No valid cookies found."
                    : report.rejected().getFirst().reason();
            throw new IllegalArgumentException(reason);
        }
        List<StoredCookie> cookies = new ArrayList<>();
        for (CookieHeaderParser.ParsedCookie parsed : report.accepted()) {
            if (parsed.isDeletion()) {
                continue;
            }
            CookieExpiration expiration = parsed.toCookieExpiration()
                    .orElseThrow(() -> new IllegalArgumentException("Cookie expiration is invalid"));
            cookies.add(new StoredCookie(
                    UUID.randomUUID(),
                    parsed.name(),
                    parsed.value(),
                    parsed.domain(),
                    parsed.path(),
                    parsed.hostOnly(),
                    parsed.secure(),
                    parsed.httpOnly(),
                    expiration,
                    now,
                    now));
        }
        if (cookies.isEmpty()) {
            throw new IllegalArgumentException("Paste only contained cookie deletions.");
        }
        return cookies;
    }

    private Optional<StoredCookie> editCookie(StoredCookie existing) {
        Instant now = Instant.now();
        Dialog<StoredCookie> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("jREQ — " + (existing == null ? "Add cookie" : "Edit cookie"));
        dialog.setHeaderText(existing == null ? "Add cookie" : "Edit cookie");

        TextField name = new TextField(existing == null ? "" : existing.name());
        PasswordField maskedValue = new PasswordField();
        maskedValue.setText(existing == null ? "" : existing.value());
        TextField plainValue = new TextField(maskedValue.getText());
        AtomicBoolean synchronizing = new AtomicBoolean();
        maskedValue.textProperty().addListener((observable, oldValue, updated) ->
                synchronizeValue(synchronizing, plainValue, updated));
        plainValue.textProperty().addListener((observable, oldValue, updated) ->
                synchronizeValue(synchronizing, maskedValue, updated));
        ToggleButton reveal = new ToggleButton("Show");
        reveal.selectedProperty().addListener((observable, oldValue, showing) ->
                reveal.setText(showing ? "Hide" : "Show"));
        plainValue.visibleProperty().bind(reveal.selectedProperty());
        plainValue.managedProperty().bind(plainValue.visibleProperty());
        maskedValue.visibleProperty().bind(reveal.selectedProperty().not());
        maskedValue.managedProperty().bind(maskedValue.visibleProperty());
        StackPane valueFields = new StackPane(maskedValue, plainValue);
        HBox valueRow = new HBox(8, valueFields, reveal);
        HBox.setHgrow(valueFields, Priority.ALWAYS);

        String defaultDomain = currentUri.getHost() == null ? "example.com" : currentUri.getHost();
        TextField domain = new TextField(existing == null ? defaultDomain : existing.domain());
        TextField path = new TextField(existing == null ? "/" : existing.path());
        CheckBox hostOnly = new CheckBox("Host only");
        hostOnly.setSelected(existing == null || existing.hostOnly());
        CheckBox secure = new CheckBox("Secure");
        secure.setSelected(existing != null && existing.secure());
        CheckBox httpOnly = new CheckBox("HttpOnly");
        httpOnly.setSelected(existing != null && existing.httpOnly());
        CheckBox session = new CheckBox("Session cookie");
        session.setSelected(existing == null || existing.isSession());
        TextField expires = new TextField(existing != null && existing.expiration() instanceof CookieExpiration.At at
                ? at.instant().toString() : "");
        expires.setPromptText("2026-12-31T23:59:59Z");
        expires.disableProperty().bind(session.selectedProperty());
        Label validation = new Label();
        validation.getStyleClass().add("validation-message");
        validation.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(9);
        addRow(form, 0, "Name", name);
        addRow(form, 1, "Value", valueRow);
        addRow(form, 2, "Domain", domain);
        addRow(form, 3, "Path", path);
        addRow(form, 4, "Expires", expires);
        form.add(new HBox(12, hostOnly, secure, httpOnly, session), 1, 5);
        form.add(validation, 1, 6);
        GridPane.setHgrow(valueRow, Priority.ALWAYS);
        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        dialog.getDialogPane().setContent(formScroll);
        ButtonType save = DialogButtons.save();
        dialog.getDialogPane().getButtonTypes().addAll(save, DialogButtons.cancel());
        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                buildCookie(existing, name.getText(), maskedValue.getText(), domain.getText(), path.getText(),
                        hostOnly.isSelected(), secure.isSelected(), httpOnly.isSelected(),
                        session.isSelected(), expires.getText(), now);
            } catch (IllegalArgumentException invalid) {
                validation.setText(invalid.getMessage());
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button.equals(save)
                ? buildCookie(existing, name.getText(), maskedValue.getText(), domain.getText(), path.getText(),
                hostOnly.isSelected(), secure.isSelected(), httpOnly.isSelected(),
                session.isSelected(), expires.getText(), now)
                : null);
        style(dialog);
        dialog.setResizable(true);
        double width = layoutMode == ResponsiveLayoutMode.COMPACT ? 460 : 560;
        double height = layoutMode == ResponsiveLayoutMode.COMPACT ? 520 : 480;
        if (owner != null && owner.getWidth() > 0) {
            width = Math.min(width, owner.getWidth() * 0.9);
            height = Math.min(height, owner.getHeight() * 0.86);
        }
        dialog.getDialogPane().setPrefSize(width, height);
        return dialog.showAndWait();
    }

    private StoredCookie buildCookie(
            StoredCookie existing,
            String name,
            String value,
            String domain,
            String path,
            boolean hostOnly,
            boolean secure,
            boolean httpOnly,
            boolean session,
            String expires,
            Instant now
    ) {
        CookieExpiration expiration;
        try {
            expiration = session ? CookieExpiration.session() : CookieExpiration.at(Instant.parse(expires.strip()));
        } catch (DateTimeParseException invalidExpiration) {
            throw new IllegalArgumentException("Expires must be an ISO-8601 instant.");
        }
        return new StoredCookie(
                existing == null ? UUID.randomUUID() : existing.id(),
                name.strip(), value, domain, path, hostOnly, secure, httpOnly, expiration,
                existing == null ? now : existing.createdAt(), now);
    }

    private void addRow(GridPane form, int row, String label, Node field) {
        form.add(new Label(label), 0, row);
        form.add(field, 1, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
    }

    private void synchronizeValue(AtomicBoolean synchronizing, TextField target, String value) {
        if (synchronizing.get()) {
            return;
        }
        synchronizing.set(true);
        target.setText(value);
        synchronizing.set(false);
    }

    private void refreshScopes() {
        String selected = scopes.getSelectionModel().getSelectedItem();
        List<String> items = new java.util.ArrayList<>();
        items.add(CURRENT_URL);
        items.addAll(model.domains());
        scopes.setItems(FXCollections.observableArrayList(items));
        if (selected != null && items.contains(selected)) {
            scopes.getSelectionModel().select(selected);
        } else {
            scopes.getSelectionModel().selectFirst();
        }
        refreshTable(scopes.getSelectionModel().getSelectedItem());
    }

    private void refreshTable(String scope) {
        List<StoredCookie> values = CURRENT_URL.equals(scope)
                ? model.currentUrlCookies()
                : scope == null ? List.of() : model.cookiesForDomain(scope);
        table.setItems(FXCollections.observableArrayList(values));
    }

    private void selectDomain(String domain) {
        scopes.getSelectionModel().select(domain);
        refreshTable(domain);
    }

    private void style(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().addAll(
                Objects.requireNonNull(getClass().getResource("/css/theme.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/components.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/responsive.css")).toExternalForm());
    }

    private void configureSize(Dialog<?> dialog) {
        double width = layoutMode == ResponsiveLayoutMode.COMPACT ? 700 : 1_020;
        double height = layoutMode == ResponsiveLayoutMode.COMPACT ? 720 : 620;
        if (owner != null && owner.getWidth() > 0 && owner.getHeight() > 0) {
            width = Math.min(width, owner.getWidth() * 0.94);
            height = Math.min(height, owner.getHeight() * 0.90);
        }
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(width, height);
    }
}
