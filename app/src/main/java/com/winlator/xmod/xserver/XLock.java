package com.winlator.xmod.xserver;

public interface XLock extends AutoCloseable {
    @Override
    void close();
}
