/**
 * Handles low-level loading C-struct type things and (optionally compressed)
 * byte ranges from low level I/O
 */
package edu.vanderbilt.accre.laurelin.root_proxy;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ROOTFile {
    public class FileBackedBuf implements BackingBuf {
        ROOTFile fh;

        protected FileBackedBuf(ROOTFile fh) {
            this.fh = fh;
        }

        @Override
        public ByteBuffer read(long off, long len) throws IOException {
            return fh.read(off,  len);
        }

        @Override
        public boolean hasLimit() throws IOException {
            return true;
        }

        @Override
        public long getLimit() throws IOException {
            return fh.getLimit();
        }

        @Override
        public BackingBuf duplicate() {
            return new FileBackedBuf(fh);
        }
    }

    private FileInterface fh;

    /* Hide constructor */
    private ROOTFile() { }

    public static ROOTFile getInputFile(String path) throws IOException {
        ROOTFile rf = new ROOTFile();
        rf.fh = IOFactory.openForRead(path);
        return rf;
    }

    public long getLimit() throws IOException {
        return fh.getLimit();
    }

    /*
     * To enable correct caching, any ByteByte buffers that get passed to
     * users must be copies of the internal ByteBuffers we have. Otherwise
     * we couldn't change the contents without breaking the users
     */
    private ByteBuffer readUnsafe(long offset, long l) throws IOException {
        /*
         * This bytebuffer can be a copy of the internal cache
         */
        return fh.read(offset, l);
    }

    public ByteBuffer read(long offset, long len) throws IOException {
        /*
         * TODO:
         * This bytebuffer must be a completely new and unlinked buffer, so
         * copy the internal array to a new one to make sure there's nothing
         * tying them together
         */
        return readUnsafe(offset, len);
    }

    public Cursor getCursor(long off) {
        return new Cursor(new FileBackedBuf(this), off);
    }
}
