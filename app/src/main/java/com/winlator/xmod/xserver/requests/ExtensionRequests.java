package com.winlator.xmod.xserver.requests;

import static com.winlator.xmod.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import com.winlator.xmod.xconnector.XInputStream;
import com.winlator.xmod.xconnector.XOutputStream;
import com.winlator.xmod.xconnector.XStreamLock;
import com.winlator.xmod.xserver.XClient;
import com.winlator.xmod.xserver.errors.XRequestError;
import com.winlator.xmod.xserver.extensions.Extension;

import java.io.IOException;

public abstract class ExtensionRequests {
    public static void queryExtension(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        short length = inputStream.readShort();
        inputStream.skip(2);
        String name = inputStream.readString8(length);
        Extension extension = client.xServer.getExtensionByName(name);
        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);

            if (extension != null) {
                outputStream.writeByte((byte)1);
                outputStream.writeByte(extension.getMajorOpcode());
                outputStream.writeByte(extension.getFirstEventId());
                outputStream.writeByte(extension.getFirstErrorId());
                outputStream.writePad(20);
            }
            else {
                outputStream.writeByte((byte)0);
                outputStream.writePad(23);
            }
        }
    }
}
