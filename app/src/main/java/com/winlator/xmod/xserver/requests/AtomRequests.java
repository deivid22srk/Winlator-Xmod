package com.winlator.xmod.xserver.requests;

import static com.winlator.xmod.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import com.winlator.xmod.xconnector.XInputStream;
import com.winlator.xmod.xconnector.XOutputStream;
import com.winlator.xmod.xconnector.XStreamLock;
import com.winlator.xmod.xserver.Atom;
import com.winlator.xmod.xserver.XClient;
import com.winlator.xmod.xserver.errors.BadAtom;
import com.winlator.xmod.xserver.errors.XRequestError;

import java.io.IOException;

public abstract class AtomRequests {
    public static void internAtom(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        boolean onlyIfExists = client.getRequestData() == 1;
        short length = inputStream.readShort();
        inputStream.skip(2);
        String name = inputStream.readString8(length);
        int id = onlyIfExists ? Atom.getId(name) : Atom.internAtom(name);
        if (id < 0) throw new BadAtom(id);

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeInt(id);
            outputStream.writePad(20);
        }
    }
    public static void getAtomName(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int id = inputStream.readInt();
        if (id < 0) throw new BadAtom(id);
        String name = Atom.getName(id);
        short length = (short) name.length();


        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt((length + 22) / 4);
            outputStream.writeString8(name);
            outputStream.writePad(22);
        }
    }
}
