package org.orcatech.mobileresearchtool.storage;

// these are data streams that are only supported on iOS
// see: https://github.com/onnela-lab/beiwe/wiki/Supported-Data-Streams
public enum DataStreamUnsupported {
    proximity, magnetometer, devicemotion, reachability;
}
