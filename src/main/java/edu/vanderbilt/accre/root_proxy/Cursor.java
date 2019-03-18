package edu.vanderbilt.accre.root_proxy;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Represents a pseudo "file pointer" into pluggable backings. The ROOTFile
 * interface will return a Cursor object that lets the user deserialize
 * different "POD"-esque types like floats, ints, but also more complicated
 * types like c-strings, etc.. The cursor object keeps track of its current
 * offset, so multiple cursors can be used in a thread-safe manner. Sub-cursors
 * can be created from other cursors to handle (e.g.) accessing decompressed
 * byte ranges using the same cursor interface. For future buffer management,
 * cursors track their parents up to the ultimate truth interface. This will
 * let us eventually store soft-references to underlying ByteBuffer objects
 * so they can be more easily GCd by java
 */
public class Cursor {
	/**
	 * Where "zero" is for this buffer. Sub-cursors start at their initialized
	 * position
	 */
	protected long base;
	
	/**
	 * Needed to get Streamers working
	 */
	protected long origin;

	/**
	 * The current offset into this buffer
	 */
	protected long off;
	private BackingBuf buf;
	private Cursor parent;
	
	public Cursor(BackingBuf impl, long base) {
		this.buf = impl;
		this.base = base;
		this.origin = 0;
	}
	
	public Cursor duplicate() {
		Cursor ret = new Cursor(buf, base);
		ret.setOffset(off);
		ret.parent = this.parent;
		ret.origin = this.origin;
		return ret;
	}
	
	public Cursor getSubcursor(long off) {
		Cursor tmp = this.duplicate();
		tmp.setOffset(off);
		return tmp;
	}
	
	public long getOrigin() {
		return origin;
	}
	
	public long getBase() {
		if (parent != null) {
			return base + parent.getBase();
		} else {
			return base;
		}
	}
	
	/*
	 * Stolen from uproot
	 */
	
//	def _startcheck(source, cursor):
//	    start = cursor.index
//	    cnt, vers = cursor.fields(source, _startcheck._format_cntvers)
//	    if numpy.int64(cnt) & uproot.const.kByteCountMask:
//	        cnt = int(numpy.int64(cnt) & ~uproot.const.kByteCountMask)
//	        return start, cnt + 4, vers
//	    else:
//	        cursor.index = start
//	        vers, = cursor.fields(source, _startcheck._format_cntvers2)
//	        return start, None, vers
//	_startcheck._format_cntvers = struct.Struct(">IH")
//	_startcheck._format_cntvers2 = struct.Struct(">H")
	public void startCheck() throws IOException {
		long off = getOffset();
		long cnt = readUInt();
		int vers = readUShort();
		if ((cnt & ~ Constants.kByteCountMask) != 0) {
			cnt = cnt & ~ Constants.kByteCountMask;
		} else {
			setOffset(off);
			vers = readUShort();
		}
	}

//	def _endcheck(start, cursor, cnt):
//	    if cnt is not None:
//	        observed = cursor.index - start
//	        if observed != cnt:
//	            raise ValueError("object has {0} bytes; expected {1}".format(observed, cnt))
	/**
	 * Gets a subcursor that might be compressed. You can tell if it's
	 * compressed if compressedLen != uncompressedLen. If that's true, read
	 * ROOT's "compression frame" to get algorithm & parameters
	 *
	 * @param off Beginning of this possibly compressed byterange
	 * @param compressedLen Length of this byterange in the parent
	 * @param uncompressedLen Length of this byterange after (possible) decompression
	 * @return Cursor pointing into new byterange
	 */
	public Cursor getPossiblyCompressedSubcursor(long off, int compressedLen, int uncompressedLen, int keyLen) {
		BackingBuf bbuf = new PossiblyCompressedBuf(this, off, compressedLen, uncompressedLen);
		Cursor ret = new Cursor(bbuf, 0);
		if (compressedLen == uncompressedLen) {
			ret.origin = keyLen;
		} else {
			ret.origin = -keyLen;
		}
		ret.parent = this;
		return ret;
	}
	

	public long getOffset() { return off; }

	public void setOffset(long newoff) { off = newoff; }

	public void skipBytes(long amount) { off += amount; }

	public ByteBuffer readBuffer(long offset, int len) throws IOException {
		return buf.read(base + offset, len);
	}

	public ByteBuffer readBuffer(int len) throws IOException {
		ByteBuffer ret = buf.read(off, len);
		off += ret.limit();
		return ret;
	}
	/*
	 * Map 1,2,4,8-byte (un)signed integers to java types
	 */

	
	public byte readChar(long offset) throws IOException {
		return buf.read(base + offset, 1).get(0);
	}
	
	public byte readChar() throws IOException {
		byte ret = readChar(off);
		off += 1;
		return ret;
	}
	
	public short readUChar(long offset) throws IOException {
		short ret = buf.read(base + offset, 1).get(0);
		if (ret < 0)
			ret += 256;
		return ret;
	}
	
	public short readUChar() throws IOException {
		short ret = readUChar(off);
		off += 1;
		return ret;
	}
	
	public short readShort(long offset) throws IOException {
		return buf.read(base + offset, 2).getShort(0);
	}
	
	public short readShort() throws IOException {
		short ret = readShort(off);
		off += 2;
		return ret;
	}
	
	public int readUShort(long offset) throws IOException {
		int ret = buf.read(base + offset, 2).getShort(0);
		if (ret < 0)
			ret += 65536L;
		return ret;
	}
	
	public int readUShort() throws IOException {
		int ret = readUShort(off);
		off += 2;
		return ret;
	}
	
	public int readInt(long offset) throws IOException {
		return buf.read(base + offset, 4).getInt(0);
	}
	
	public int readInt() throws IOException {
		int ret = readInt(off);
		off += 4;
		return ret;
	}
	
	public int[] readIntArray(int len) throws IOException {
		int []ret = new int[len];
		buf.read(off + base, len * 4).asIntBuffer().get(ret, 0, len);
		off += len * 4;
		return ret;
	}
	
	public long readUInt(long offset) throws IOException {
		long ret = buf.read(base + offset, 4).getInt(0);
		if (ret < 0)
			ret += 4294967296L;
		return ret;
	}
	
	public long readUInt() throws IOException {
		long ret = readUInt(off);
		off += 4;
		return ret;
	}
	
	public long readLong(long offset) throws IOException {
		return buf.read(base + offset, 8).getLong(0);
	}
	
	public long readLong() throws IOException {
		byte ret = readChar(off);
		off += 8;
		return ret;
	}
	
	public long[] readLongArray(int len) throws IOException {
		long []ret = new long[len];
		buf.read(base + off, len * 8).asLongBuffer().get(ret, 0, len);
		off += len * 8;
		return ret;
	}
	
	public BigInteger readULong(long offset) throws IOException {		
		BigInteger ret = new BigInteger(buf.read(base + offset, 8).array());
		if (ret.compareTo(BigInteger.ZERO) == -1) {
			ret = ret.add(BigInteger.valueOf(2).pow(64));
		}
		return ret;
	}
	
	public BigInteger readULong() throws IOException {
		BigInteger ret = readULong(off);
		off += 8;
		return ret;
	}

	/*
	 * Floating point values
	 */
	public float readFloat(long offset) throws IOException {
		return buf.read(base + offset, 4).getFloat(0);
	}
	
	public float readFloat() throws IOException {
		float ret = readFloat(off);
		off += 4;
		return ret;
	}
	
	public double readDouble(long offset) throws IOException {
		return buf.read(base + offset, 8).getDouble(0);
	}
	
	public double readDouble() throws IOException {
		double ret = readDouble(off);
		off += 8;
		return ret;
	}
	
	public double[] readDoubleArray(int len) throws IOException {
		double []ret = new double[len];
		buf.read(base + off, len * 8).asDoubleBuffer().get(ret, 0, len);
		off += len * 8;
		return ret;
	}
	
	public String readTString(long offset) throws IOException {
		int l = readUChar(offset);
		offset += 1;
		if (l < 0)                                                             
			l += 256;

		if (l == 255) {
			offset -= 1;
			l = readInt(offset) & 0x00FFFFFF;
			offset += 4;
		}
		ByteBuffer bytes = buf.read(base + offset, l);
		byte[] rawbytes = new byte[l];
		bytes.position(0);
		bytes.get(rawbytes, 0, l);
		String ret;
		if (l == 0) {
			ret = new String();
		} else {
			ret = new String(rawbytes);
		}
		return ret;
	}
	
	public String readTString() throws IOException {
		String ret = readTString(off);
		if (ret.length() < 255) {
			// only one length byte
			off += ret.length() + 1;
		} else {
			// first length byte is 255, then the next 3 bytes
			off += ret.length() + 1 + 3;
		}
		return ret;
	}

	public String readCString(long offset) throws IOException {
		int BUF_SIZE = 1024;
		boolean done = false;
		int len = 0;
		long buf_start = offset;
		ByteBuffer tmpbuf;
		String ret = new String();
		while (!done) {
			int real_len = BUF_SIZE;
			if (buf.hasLimit()) {
				real_len = Math.min(BUF_SIZE, (int)(buf.getLimit() - base - buf_start));
				done = true;
			}
			tmpbuf = buf.read(base + buf_start, real_len);
			int x;
			for (x = 0; x < tmpbuf.limit() && tmpbuf.get(x) != '\0'; x += 1, len += 1) {
//									" - " +
//									String.format("0x%02X", buf.read(base + buf_start + x, 1).get()));
				ret += (char) tmpbuf.get(x);
			}
			if (tmpbuf.get(x) == '\0') {
				break;
			}
			buf_start += BUF_SIZE;
		}
//		tmpbuf = buf.read(base + offset, len);
//		Charset charset = StandardCharsets.US_ASCII;
//		return new String(tmpbuf.array(), charset);
		return ret;
	}
	
	public String readCString() throws IOException {
		String ret = readCString(off);
		// Skip the null byte
		off += ret.length() + 1;
		return ret;
	}
		
}
