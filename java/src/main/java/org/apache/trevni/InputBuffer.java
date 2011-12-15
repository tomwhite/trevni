/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.trevni;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/** */

class InputBuffer {
  private SeekableInput in;

  private long inLength;
  private long offset;                            // position of buffer in input

  private byte[] buf;                             // data from input
  private int pos;                                // position within buffer
  private int limit;                              // end of valid buffer data

  public InputBuffer(SeekableInput in) {
    this.in = in;
    this.inLength = in.length();

    if (in instanceof SeekableByteArrayInput) {   // use buffer directly
      this.buf = ((SeekableByteArrayInput)in).getBuffer();
      this.limit = in.length();
    } else {                                      // create new buffer
      this.buf = new byte[8192];
    }
  }

  public void readFully(byte[] bytes, int start, int length) throws IOException {
    int remaining = limit - pos;
    if (length <= remaining) {
      System.arraycopy(buf, pos, bytes, start, length);
      pos += length;
    } else {                                      // read remainder of buffer
      System.arraycopy(buf, pos, bytes, start, remaining);
      start += remaining;
      length -= remaining;
      pos = limit;
      in.readFully(bytes, start, length);         // finish from input
    }
  }

  public long tell() { return offset + pos; }

  public long length() { return inLength; }

  public void seek(long position) {
    if (position >= offset && position <= tell()) {
      pos = position - offset;                    // seek in buffer;
      return;
    }
    pos = 0;
    limit = 0;
    in.seek(position);
  }

  public int readInt() throws IOException {
    ensure(5);                               // won't throw index out of bounds
    int len = 1;
    int b = buf[pos] & 0xff;
    int n = b & 0x7f;
    if (b > 0x7f) {
      b = buf[pos + len++] & 0xff;
      n ^= (b & 0x7f) << 7;
      if (b > 0x7f) {
        b = buf[pos + len++] & 0xff;
        n ^= (b & 0x7f) << 14;
        if (b > 0x7f) {
          b = buf[pos + len++] & 0xff;
          n ^= (b & 0x7f) << 21;
          if (b > 0x7f) {
            b = buf[pos + len++] & 0xff;
            n ^= (b & 0x7f) << 28;
            if (b > 0x7f) {
              throw new IOException("Invalid int encoding");
            }
          }
        }
      }
    }
    pos += len;
    if (pos > limit) {
      throw new EOFException();
    }
    return (n >>> 1) ^ -(n & 1); // back to two's-complement
  }

  public long readLong() throws IOException {
    ensure(10);
    int b = buf[pos++] & 0xff;
    int n = b & 0x7f;
    long l;
    if (b > 0x7f) {
      b = buf[pos++] & 0xff;
      n ^= (b & 0x7f) << 7;
      if (b > 0x7f) {
        b = buf[pos++] & 0xff;
        n ^= (b & 0x7f) << 14;
        if (b > 0x7f) {
          b = buf[pos++] & 0xff;
          n ^= (b & 0x7f) << 21;
          if (b > 0x7f) {
            // only the low 28 bits can be set, so this won't carry
            // the sign bit to the long
            l = innerLongDecode((long)n);
          } else {
            l = n;
          }
        } else {
          l = n;
        }
      } else {
        l = n;
      }
    } else {
      l = n;
    }
    if (pos > limit) {
      throw new EOFException();
    }
    return (l >>> 1) ^ -(l & 1); // back to two's-complement
  }
  
  // splitting readLong up makes it faster because of the JVM does more
  // optimizations on small methods
  private long innerLongDecode(long l) throws IOException {
    int len = 1;
    int b = buf[pos] & 0xff;
    l ^= (b & 0x7fL) << 28;
    if (b > 0x7f) {
      b = buf[pos + len++] & 0xff;
      l ^= (b & 0x7fL) << 35;
      if (b > 0x7f) {
        b = buf[pos + len++] & 0xff;
        l ^= (b & 0x7fL) << 42;
        if (b > 0x7f) {
          b = buf[pos + len++] & 0xff;
          l ^= (b & 0x7fL) << 49;
          if (b > 0x7f) {
            b = buf[pos + len++] & 0xff;
            l ^= (b & 0x7fL) << 56;
            if (b > 0x7f) {
              b = buf[pos + len++] & 0xff;
              l ^= (b & 0x7fL) << 63;
              if (b > 0x7f) {
                throw new IOException("Invalid long encoding");
              }
            }
          }
        }
      }
    }
    pos += len;
    return l;
  }

  public float readFloat() throws IOException {
    ensure(4);
    int len = 1;
    int n = (buf[pos] & 0xff) | ((buf[pos + len++] & 0xff) << 8)
        | ((buf[pos + len++] & 0xff) << 16) | ((buf[pos + len++] & 0xff) << 24);
    if ((pos + 4) > limit) {
      throw new EOFException();
    }
    pos += 4;
    return Float.intBitsToFloat(n);
  }

  public double readDouble() throws IOException {
    ensure(8);
    int len = 1;
    int n1 = (buf[pos] & 0xff) | ((buf[pos + len++] & 0xff) << 8)
        | ((buf[pos + len++] & 0xff) << 16) | ((buf[pos + len++] & 0xff) << 24);
    int n2 = (buf[pos + len++] & 0xff) | ((buf[pos + len++] & 0xff) << 8)
        | ((buf[pos + len++] & 0xff) << 16) | ((buf[pos + len++] & 0xff) << 24);
    if ((pos + 8) > limit) {
      throw new EOFException();
    }
    pos += 8;
    return Double.longBitsToDouble((((long) n1) & 0xffffffffL)
        | (((long) n2) << 32));
  }

  private ByteBuffer scratch = ByteBuffer.allocate(16);
  private static final Charset UTF8 = Charset.forName("UTF-8");

  public String readString() throws IOException {
    scratch = readBytes(scratch);
    return new String(scratch.array(), 0, scratch.limit(), UTF8);
  }  

  public byte[] readBytes() throws IOException {
    int length = readInt();
    result = new byte[length];
    readFully(result, 0, length);
    return result;

  }

  public ByteBuffer readBytes(ByteBuffer old) throws IOException {
    int length = readInt();
    ByteBuffer result;
    if (old != null && length <= old.capacity()) {
      result = old;
      result.clear();
    } else {
      result = ByteBuffer.allocate(length);
    }
    readFully(result.array(), result.position(), length);
    result.limit(length);
    return result;
  }

  public void skipBytes() throws IOException {
    skip(readInt());
  }

  private void skip(long length) throws IOException {
    seek(tell()+length);
  }

  private void ensure(int num) throws IOException {
    int remaining = limit - pos;
    if (remaining < num) {                       // move remaining to front
      System.arraycopy(buf, pos, buf, 0, remaining); 
      pos = 0;
      limit = remaining + tryRead(buf, start+remaining, buf.length-remaining);
    }
  }

  private int tryRead(byte[] data, int off, int len) throws IOException {
    int remaining = len;
    try {
      while (remaining > 0) {
        int read = in.read(data, off, remaining);
        if (read < 0)
          break;
        remaining -= read;
        off += read;
      }
    } catch (EOFException eof) {}
    return len - remaining;
  }

}