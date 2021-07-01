package org.orcatech.mobileresearchtool.storage;

public enum DataStreamPermission {
    requested, disabled, enabled, denied;

    public boolean isRequested() {
        return this == requested;
    }

    public boolean isDisabled() {
        return this == disabled;
    }

    public boolean isEnabled() {
        return this == enabled;
    }

    public boolean isDenied() {
        return this == denied;
    }
}
