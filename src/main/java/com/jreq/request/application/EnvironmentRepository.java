package com.jreq.request.application;

import com.jreq.request.domain.EnvironmentSelection;
import com.jreq.request.domain.RequestLocation;

import java.util.List;

public interface EnvironmentRepository {
    EnvironmentConfiguration loadConfiguration();

    List<EnvironmentActivation> findActivations();

    void saveConfiguration(EnvironmentConfiguration configuration);

    void saveSelection(RequestLocation location, EnvironmentSelection selection);
}
