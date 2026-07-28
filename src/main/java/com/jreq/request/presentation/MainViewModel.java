package com.jreq.request.presentation;

import com.jreq.request.domain.HttpMethod;
import com.jreq.shared.ui.ResponsiveLayoutMode;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class MainViewModel {
    private final ObjectProperty<HttpMethod> selectedMethod =
            new SimpleObjectProperty<>(HttpMethod.GET);
    private final StringProperty url = new SimpleStringProperty("");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty sidebarExpanded = new SimpleBooleanProperty(true);
    private final StringProperty selectedRequestTab = new SimpleStringProperty("Params");
    private final StringProperty selectedResponseTab = new SimpleStringProperty("Body");
    private final StringProperty responseStatus = new SimpleStringProperty("—");
    private final StringProperty responseDuration = new SimpleStringProperty("—");
    private final StringProperty responseSize = new SimpleStringProperty("—");
    private final StringProperty responseBody = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("Ready");
    private final ObjectProperty<ResponsiveLayoutMode> responsiveMode =
            new SimpleObjectProperty<>(ResponsiveLayoutMode.NORMAL);

    public void sendRequest() {
        if (loading.get()) {
            return;
        }
        statusMessage.set(url.get().isBlank()
                ? "Enter a request URL"
                : "Request execution is not connected yet");
    }

    public void newRequest() {
        selectedMethod.set(HttpMethod.GET);
        url.set("");
        responseBody.set("");
        responseStatus.set("—");
        responseDuration.set("—");
        responseSize.set("—");
        statusMessage.set("New unsaved request");
    }

    public void toggleSidebar() {
        sidebarExpanded.set(!sidebarExpanded.get());
    }

    public ObjectProperty<HttpMethod> selectedMethodProperty() {
        return selectedMethod;
    }

    public StringProperty urlProperty() {
        return url;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public BooleanProperty sidebarExpandedProperty() {
        return sidebarExpanded;
    }

    public StringProperty selectedRequestTabProperty() {
        return selectedRequestTab;
    }

    public StringProperty selectedResponseTabProperty() {
        return selectedResponseTab;
    }

    public StringProperty responseStatusProperty() {
        return responseStatus;
    }

    public StringProperty responseDurationProperty() {
        return responseDuration;
    }

    public StringProperty responseSizeProperty() {
        return responseSize;
    }

    public StringProperty responseBodyProperty() {
        return responseBody;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public ObjectProperty<ResponsiveLayoutMode> responsiveModeProperty() {
        return responsiveMode;
    }
}
