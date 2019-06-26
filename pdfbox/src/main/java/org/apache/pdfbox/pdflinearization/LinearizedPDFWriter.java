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
package org.apache.pdfbox.pdflinearization;


import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static java.lang.Math.ceil;
import static java.lang.Math.max;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfparser.PDFXRefStream;
import org.apache.pdfbox.pdfwriter.COSStandardOutputStream;
import org.apache.pdfbox.pdfwriter.COSWriter;
import static org.apache.pdfbox.pdfwriter.COSWriter.COMMENT;
import static org.apache.pdfbox.pdfwriter.COSWriter.ENDSTREAM;
import static org.apache.pdfbox.pdfwriter.COSWriter.STREAM;
import org.apache.pdfbox.pdfwriter.COSWriterXRefEntry;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.Charsets;


import org.apache.pdfbox.cos.ReferencedCOSStream;


/**
 * Subclass of COSWriter specialized for use while generating linearized PDF
 * files. Written content must be retrieved with getAndReset(), it will not be
 * written to an external file.
 *
 * @author  Marian Gavalier
 * @version $Id$
 */
class LinearizedPDFWriter
   extends COSWriter
{
   //~ Static fields/initializers --------------------------------------------------------------------------------------------------------------------

   /** Comparator for XRef-entries */
   static final Comparator<COSWriterXRefEntry> XREFCOMP     = (COSWriterXRefEntry lhs,
                                                               COSWriterXRefEntry rhs) ->
                                                              {
                                                                 final long lhsInt = lhs.getKey().getNumber();
                                                                 final long rhsInt = rhs.getKey().getNumber();

                                                                 return (int)(rhsInt - lhsInt);
                                                              };

   //~ Instance members ------------------------------------------------------------------------------------------------------------------------------

   private List<VirtualPart> writtenParts;

   private long              lastLength = -1;
   private int               lastCount  = 0;

   //~ Constructors ----------------------------------------------------------------------------------------------------------------------------------

   /**
    * Default constructor
    *
    * @param doc The document that should be written
    */
   LinearizedPDFWriter(final COSDocument doc)
   {
      super(new ByteArrayOutputStream());
      this.pdDocument = new PDDocument(doc);
      writtenParts    = new ArrayList<>();
   }

   //~ Methods ---------------------------------------------------------------------------------------------------------------------------------------

   private COSWriterXRefEntry getFakeEntry(final int i)
   {
      return new COSWriterXRefEntry(ThreadLocalRandom.current().nextInt(), null, new COSObjectKey(i, 0));
   }


   /**
    * Writes a dummy version of the XRef-stream for the a range of objects
    * specified by the first object number to write and the number of objects to
    * write. This will give an upper estimate of the size of the real stream. In
    * order to prevent the compression algorithm to compress too much, randomly
    * generated data is used to ensure a high entropy. This is a bad hack and
    * may break in rare cases.
    *
    * @param  doc             The document used to create the stream
    * @param  xRefRangesStart The first object number to include
    * @param  xRefLength      The number of objects
    * @param  trailer         The trailer to retrieve information from for the
    *                         stream
    * @param  xRefId          Id of the xref stream
    * @param  xRefOffset      The offset to write at the end, referring to the
    *                         other stream (see Adobe pdf spec)
    *
    * @throws IOException
    */
   void doWriteXrefRangeDummy(final COSDocument   doc,
                              final int           xRefRangesStart,
                              final long          xRefLength,
                              final COSDictionary trailer,
                              final int           xRefId,
                              final long          xRefOffset)
      throws IOException
   {
      final long          startPos      = this.getPos();
      final PDFXRefStream pdfxRefStream = new PDFXRefStream(doc);

      for (int i = 0; i < (xRefLength + 1); ++i)
      {
         pdfxRefStream.addEntry(this.getFakeEntry(i + xRefRangesStart));
      }

      // the size is the highest object number+1
      pdfxRefStream.addTrailerInfo(trailer);
      pdfxRefStream.setSize(trailer.getInt(COSName.SIZE));

      final COSStream stream2 = pdfxRefStream.getStream();

      this.getObjectKeys().put(stream2, new COSObjectKey(xRefId, 0));
      doWriteObject(stream2);

      final long endPos   = this.getPos();
      final long length   = endPos - startPos;

      // Take potential compressing overhead into account
      final long overflow = (long)max(ceil(length * 0.003), 11);

      this.doWriteSpaces(overflow);
      writeEndXrefStream(xRefOffset);
   }


   private void writeEndXrefStream(final long xRefOffset)
      throws IOException
   {
      getStandardOutput().write(STARTXREF);
      getStandardOutput().writeEOL();
      getStandardOutput().write(String.valueOf(xRefOffset).getBytes(Charsets.ISO_8859_1));
      getStandardOutput().writeEOL();
      getStandardOutput().write(EOF);
      getStandardOutput().writeEOL();
   }


   /**
    * Write the xref-stream for the range of objects specified. This will write
    * the stream for the entries specified.
    *
    * @param  doc        The object used to initialize the stream
    * @param  xRefLength The number of entries to be included in the stream.
    *                    Used for sanity check
    * @param  entries    The entries to be written
    * @param  trailer    The trailer used to init stream info
    * @param  xRefId     ID of the stream
    * @param  xRefOffset Offset referring to other xref-stream (see Adobe pdf
    *                    spec)
    * @param  startPos   Offset where the stream will begin in the completely
    *                    written document.
    *
    * @throws IOException
    */
   void doWriteXrefRange(final COSDocument              doc,
                         final long                     xRefLength,
                         final List<COSWriterXRefEntry> entries,
                         final COSDictionary            trailer,
                         final int                      xRefId,
                         final long                     xRefOffset,
                         final long                     startPos)
      throws IOException
   {
      entries.add(new COSWriterXRefEntry(startPos, null, new COSObjectKey(xRefId, 0)));
      if (xRefLength != entries.size())
      {
         throw new ArithmeticException("xRefLength != entries.size()");
      }
      entries.sort(XREFCOMP);

      final PDFXRefStream pdfxRefStream = new PDFXRefStream(doc);

      for (int i = 0; i < xRefLength; i++)
      {
         pdfxRefStream.addEntry(entries.get(i));
      }
      pdfxRefStream.addTrailerInfo(trailer);
      pdfxRefStream.setSize(trailer.getInt(COSName.SIZE));

      final COSStream stream = pdfxRefStream.getStream();

      this.getObjectKeys().put(stream, new COSObjectKey(xRefId, 0));
      doWriteObject(stream);
      writeEndXrefStream(xRefOffset);
   }


   @Override
   public Object visitFromStream(final COSStream obj)
      throws IOException
   {
      if (!(obj instanceof ReferencedCOSStream) || !((ReferencedCOSStream)obj).isReferencedStream())
      {
         return super.visitFromStream(obj);
      }

      final ReferencedCOSStream stream = (ReferencedCOSStream)obj;

      visitFromDictionary(obj);
      getStandardOutput().write(STREAM);
      getStandardOutput().writeCRLF();

      getOutput().flush();

      if (getStandardOutput().getPos() > 0)
      {
         writtenParts.add(new VirtualPart(((ByteArrayOutputStream)getOutput()).toByteArray()));
      }

      final long   startPos  = stream.getOffset();
      final long   endPos    = stream.getOffset() + stream.getLength();
      final String reference = stream.getReference().getPath();

      writtenParts.add(new VirtualPart(reference, startPos, (int)(endPos - startPos)));

      resetOutputs();

      getStandardOutput().writeCRLF();
      getStandardOutput().write(ENDSTREAM);
      getStandardOutput().writeEOL();
      return null;
   }


   /**
    * Gets the position of the writer, incorporating the lengths of all objects
    * written since the last reset. The method will cache its results, this
    * means that when resetting the writer, this cache needs to be reset as well
    * but it may be called a lot of times with no big performance penalty.
    *
    * @return Length of all written content + 1
    */
   long getPos()
   {
      if (writtenParts.isEmpty())
      {
         return getStandardOutput().getPos();
      }
      if (lastCount == 0)
      {
         lastLength = VirtualPart.calculateInflatedLength(writtenParts);
      }
      else
      {
         long length = 0;

         for (int i = lastCount - 1; i < writtenParts.size(); i++)
         {
            length += writtenParts.get(i).getInflatedLength();
         }
         lastLength = length;
      }
      lastCount = writtenParts.size();
      return lastLength + getStandardOutput().getPos();
   }


   /**
    * Resets information that object was written before. This will not delete
    * the data written but the associated xref-entry. Needs to be called before
    * an object is written the second time.
    *
    * @param obj The object to be forgotten.
    */
   void removeWrittenObject(final COSBase obj)
   {
      if (!writtenObjects.contains(obj))
      {
         return;
      }
      writtenObjects.remove(obj);
      for (final COSWriterXRefEntry entry : getXRefEntries())
      {
         if (entry.getObject() == obj)
         {
            getXRefEntries().remove(entry);
            break;
         }
      }
   }


   /**
    * Writes the object specified but will coalesce the generated VirtualParts
    * until a size of 128kb is reached. Using this method significantly reduces
    * accessing times for the VirtualParts later.
    *
    * @param  obj The object to be written
    *
    * @return Number of bytes written
    *
    * @throws IOException
    */
   long doWriteObjectInSequence(final COSBase obj)
      throws IOException
   {
      final long oldPos = this.getPos();

      this.doWriteObject(obj);
      if (getStandardOutput().getPos() > (128 * 1024))
      {
         getOutput().flush();
         getStandardOutput().flush();
         writtenParts.add(new VirtualPart(((ByteArrayOutputStream)getOutput()).toByteArray()));
         resetOutputs();
      }
      return this.getPos() - oldPos;
   }


   /**
    * Wrapper around doWriteHeader()
    *
    * @throws IOException
    */
   public void writeHeader()
      throws IOException
   {
      this.doWriteHeader(this.pdDocument.getDocument());
   }


   /**
    * Public accessor for protected getXRefEntries
    *
    * @return The xref-entries the writer has collected
    */
   public List<COSWriterXRefEntry> retrieveXRefEntries()
   {
      return this.getXRefEntries();
   }


   /**
    * This method should never be called as it is used to assign object numbers.
    * This needs to be done beforehand in the case of a linearization.
    *
    * @param newNumber [!PARA_DESCRIPTION!]
    */
   @Override
   protected void setNumber(final long newNumber)
   {
      throw new UnsupportedOperationException("Attempt to set object number in LinearizedPDFWriter. Object numbers are predetermined here and should not be changed later.");
   }


   @Override
   public void close()
      throws IOException
   {
      getOutput().flush();
      if (getStandardOutput().getPos() > 0)
      {
         writtenParts.add(new VirtualPart(((ByteArrayOutputStream)getOutput()).toByteArray()));
      }

      super.close();
   }


   /**
    * This will retrieve the parts written since the last reset and remove all
    * of them from the writer. This will not touch xref-entries nor
    * writtenObjects lists. To remove an object fro there, removeWrittenObject()
    * should be invoked.
    *
    * @return Written parts
    *
    * @throws IOException While closing OutputStream
    */
   List<VirtualPart> getAndResetParts()
      throws IOException
   {
      this.close();

      final List<VirtualPart> retVal = writtenParts;

      resetOutputs();
      writtenParts = new ArrayList<>();
      return retVal;
   }


   private void resetOutputs()
   {
      setOutput(new ByteArrayOutputStream());
      setStandardOutput(new COSStandardOutputStream(getOutput()));
   }


   private void doWriteSpaces(final long spacesCount)
      throws IOException
   {
      for (long i = 0; i < (spacesCount - 1); i++)
      {
         getStandardOutput().write(COMMENT);
      }
      getStandardOutput().writeLF();
   }


   /**
    * Fills current outputStream with % until the position given is reached.
    * This should be used when an object was written which was smaller than
    * initially anticipated.
    *
    * @param  pos Offset until which source should be filled with %.
    *
    * @throws IOException
    */
   void fillUntilPos(final long pos)
      throws IOException
   {
      long currentPos = this.getPos();
      if (pos < currentPos)
      {
         throw new ArithmeticException("LinearizedPDFWriter failed to add paddig "
              + "characters because the current position is already beyond the "
              + "requested one. This is ususally due to a failed length "
              + "estimation. Current pos: " + currentPos 
              + " Requested pos: " + pos);
      }
      this.doWriteSpaces(pos - currentPos);
   }
}
