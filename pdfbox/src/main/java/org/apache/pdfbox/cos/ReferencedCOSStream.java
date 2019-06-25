/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.pdfbox.cos;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.channels.FileChannel;

import org.apache.pdfbox.filter.DecodeOptions;
import org.apache.pdfbox.io.ScratchFile;

public class ReferencedCOSStream
   extends COSStream
{
   //~ Instance members ------------------------------------------------------------------------------------------------------------------------------

   boolean isReference = false;
   File    reference   = null;
   long    offset      = -1;
   long    length      = -1;

   //~ Constructors ----------------------------------------------------------------------------------------------------------------------------------

   ReferencedCOSStream(final ScratchFile scratchFile)
   {
      super(scratchFile);
   }

   //~ Methods ---------------------------------------------------------------------------------------------------------------------------------------


   @Override
   public COSInputStream createInputStream(final DecodeOptions options)
      throws IOException
   {
      if (this.isReference)
      {
         final InputStream in = new SlicedFileInputStream(this.reference, this.offset, this.length);

         return COSInputStream.create(getFilterList(), this, in, this.scratchFile, options);
      }
      else
      {
         return super.createInputStream(options);
      }
   }


   @Override
   public InputStream createRawInputStream()
      throws IOException
   {
      if (this.isReference)
      {
         return new SlicedFileInputStream(this.reference, this.offset, this.length);
      }
      else
      {
         return super.createRawInputStream();
      }
   }


   @Override
   public OutputStream createOutputStream(final COSBase filters)
      throws IOException
   {
      this.isReference = false;
      return super.createOutputStream(filters);
   }


   @Override
   public OutputStream createRawOutputStream()
      throws IOException
   {
      this.isReference = false;
      return super.createRawOutputStream();
   }


   public void setReference(final File file,
                            final long offset,
                            final long length)
   {
      this.isReference = true;
      this.reference   = file;
      this.offset      = offset;
      this.length      = length;
      this.setLong(COSName.LENGTH, length);
   }

   //~ Inner Classes ---------------------------------------------------------------------------------------------------------------------------------

   private final class SlicedFileInputStream
      extends FileInputStream
   {
      //~ Instance members ---------------------------------------------------------------------------------------------------------------------------

      private long       index;
      private final long length;

      //~ Constructors -------------------------------------------------------------------------------------------------------------------------------

      public SlicedFileInputStream(final File file,
                                   final long offset,
                                   final long length)
         throws FileNotFoundException, IOException
      {
         super(file);
         this.length = length;
         this.skip(offset);
         this.index = 0;
      }

      //~ Methods ------------------------------------------------------------------------------------------------------------------------------------

      @Override
      public int available()
         throws IOException
      {
         final long remaining = length - index;

         if (remaining < 0)
         {
            return 0;
         }
         return (int)remaining;
      }


      @Override
      public int read(final byte[] b)
         throws IOException
      {
         final int remaining = this.available();
         final int len       = (remaining < b.length) ? remaining : b.length;

         index += len;
         if (len > 0)
         {
            return super.read(b, 0, len);
         }
         else
         {
            return -1;
         }
      }

      @Override
      public int read(final byte[] b,
                      final int    off,
                      int          len)
         throws IOException
      {
         final int remaining = this.available();
         len   =  (remaining < len) ? remaining : len;
         index += len;
         if (len > 0)
         {
            return super.read(b, off, len);
         }
         else
         {
            return -1;
         }
      }
      
      public int read() throws IOException {
         if (this.available() > 0) {
            index += 1;
            return super.read();
         } else {
            return -1;
         }
      }

      @Override
      public long skip(final long n)
         throws IOException
      {
         index += n;
         return super.skip(n);
      }

      @Override
      public FileChannel getChannel()
      {
         throw new UnsupportedOperationException("Obtaining a FileChannel is not supported because a correct offset cannot be ensured.");
      }
      
   }
}
