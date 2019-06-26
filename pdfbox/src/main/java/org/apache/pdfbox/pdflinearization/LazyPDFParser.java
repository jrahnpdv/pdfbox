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
//package org.apache.pdfbox.pdflinearization;
//
//
//import java.io.File;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//
//import org.apache.commons.logging.Log;
//import org.apache.commons.logging.LogFactory;
//import org.apache.pdfbox.cos.COSBase;
//import org.apache.pdfbox.cos.COSDictionary;
//import org.apache.pdfbox.cos.COSName;
//import org.apache.pdfbox.cos.COSNumber;
//import org.apache.pdfbox.cos.COSObject;
//import org.apache.pdfbox.cos.COSStream;
//import org.apache.pdfbox.io.RandomAccessBuffer;
//import org.apache.pdfbox.io.RandomAccessBufferedFileInputStream;
//import org.apache.pdfbox.io.ScratchFile;
//import org.apache.pdfbox.pdfparser.EndstreamOutputStream;
//import org.apache.pdfbox.pdfparser.PDFParser;
//import static org.apache.pdfbox.util.Charsets.ISO_8859_1;
//
//
///**
// * Subclass of PDFParser which will replace long Object-Streams with references to the parsed file.
// *
// * @author  Marian Gavalier
// * @version $Id$
// */
//public class LazyPDFParser
//   extends PDFParser
//{
//   //~ Static fields/initializers --------------------------------------------------------------------------------------------------------------------
//
//   private static final Log LOG       = LogFactory.getLog(LazyPDFParser.class);
//
//   //~ Instance members ------------------------------------------------------------------------------------------------------------------------------
//
//   private final String     reference;
//
//   //~ Constructors ----------------------------------------------------------------------------------------------------------------------------------
//
//   /**
//    * Constuctor
//    *
//    * @param  file        File to be parsed
//    * @param  scratchFile File to be used for storing PDF data
//    *
//    * @throws IOException IO error
//    */
//   public LazyPDFParser(final File        file,
//                        final ScratchFile scratchFile)
//      throws IOException
//   {
//      super(new RandomAccessBufferedFileInputStream(file), scratchFile);
//      this.reference = file.toPath().toString();
//   }
//
//
//   /**
//    * Constructor which stores all parsed data in main memory
//    *
//    * @param  file File to be parsed
//    *
//    * @throws IOException IO error
//    */
//   public LazyPDFParser(final File file)
//      throws IOException
//   {
//      super(new RandomAccessBufferedFileInputStream(file));
//      this.reference = file.toPath().toString();
//   }
//
//
//   /**
//    * Alternative constructo
//    *
//    * @param  input     InputStream to be read
//    * @param  reference Path to the file that is read
//    *
//    * @throws IOException IO error
//    */
//   public LazyPDFParser(final InputStream input,
//                        final String      reference)
//      throws IOException
//   {
//      super(new RandomAccessBuffer(input));
//      this.reference = reference;
//   }
//
//   //~ Methods ---------------------------------------------------------------------------------------------------------------------------------------
//   
//   @Override
//   protected COSBase parseDirObject() throws IOException
//   {
//       COSBase retval = super.parseDirObject();
//       if (retval instanceof COSObject && ((COSObject)retval).getObject() == null) {
//           retval.setDirect(false);
//       } else {
//           retval.setDirect(true);
//       }
//       return retval;
//   }
//
//   @Override
//   protected COSStream parseCOSStream(final COSDictionary dic)
//      throws IOException
//   {
//      /*
//       * This needs to be dic.getItem because when we are parsing, the underlying object might still be null.
//       */
//      final COSNumber streamLengthObj = getLength(dic.getItem(COSName.LENGTH), dic.getCOSName(COSName.TYPE));
//
//      COSStream       stream          = document.createCOSStream(dic);
//
//      // read 'stream'; this was already tested in parseObjectsDynamically()
//      readString();
//
//      skipWhiteSpaces();
//
//      if (streamLengthObj == null)
//      {
//         if (isLenient)
//         {
//            LOG.warn("The stream doesn't provide any stream length, using fallback readUntilEnd, at offset " + source.getPosition());
//         }
//         else
//         {
//            throw new IOException("Missing length for stream.");
//         }
//      }
//
//      if ((streamLengthObj != null) && (streamLengthObj.longValue() >= 1024))
//      {
//         final long                streamBegPos = source.getPosition();
//         final ReferencedCOSStream refStream    = ReferencedCOSStream.createFromCOSStream(stream);
//
//         try
//         {
//            source.seek(source.getPosition() + streamLengthObj.longValue());
//         }
//         finally
//         {
//            stream.setItem(COSName.LENGTH, streamLengthObj);
//         }
//         refStream.setReference(new File(reference), streamBegPos, source.getPosition() - streamBegPos);
//         stream = refStream;
//      }
//      else
//      {
//         try(final OutputStream out = stream.createRawOutputStream())
//         {
//            if ((streamLengthObj != null) && validateStreamLength(streamLengthObj.longValue()))
//            {
//               readValidStream(out, streamLengthObj);
//            }
//            else
//            {
//               readUntilEndStream(new EndstreamOutputStream(out));
//            }
//         }
//         finally
//         {
//            stream.setItem(COSName.LENGTH, streamLengthObj);
//         }
//      }
//
//      final String endStream = readString();
//
//      if (endStream.equals("endobj") && isLenient)
//      {
//         LOG.warn("stream ends with 'endobj' instead of 'endstream' at offset " + source.getPosition());
//
//         // avoid follow-up warning about missing endobj
//         source.rewind(ENDOBJ.length);
//      }
//      else if ((endStream.length() > 9) && isLenient && endStream.substring(0, 9).equals(ENDSTREAM_STRING))
//      {
//         LOG.warn("stream ends with '" + endStream + "' instead of 'endstream' at offset " + source.getPosition());
//
//         // unread the "extra" bytes
//         source.rewind(endStream.substring(9).getBytes(ISO_8859_1).length);
//      }
//      else if (!endStream.equals(ENDSTREAM_STRING))
//      {
//         throw new IOException("Error reading stream, expected='endstream' actual='" + endStream + "' at offset " + source.getPosition());
//      }
//
//      return stream;
//   }
//
//}
