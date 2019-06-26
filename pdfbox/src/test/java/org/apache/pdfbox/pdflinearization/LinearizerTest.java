/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pdfbox.pdflinearization;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Jonathan Rahn
 */
public class LinearizerTest
{

   final File SRCDIR = new File("src/test/resources/input/merge");
   private static final File TARGETPDFDIR = new File("target/pdfs/linearization");

   @Before
   public void setUp() throws IOException
   {
      if (!Files.exists(TARGETPDFDIR.toPath()))
      {
         Files.createDirectory(TARGETPDFDIR.toPath());
      }
   }

   @Test
   public void testLinearization() throws IOException
   {
      int DPI = 50;
      int pageCount;
      for (File file : SRCDIR.listFiles())
      {
         try (PDDocument srcDoc = PDDocument.load(new FileInputStream(file)))
         {
            pageCount = srcDoc.getNumberOfPages();
            PDFRenderer src1PdfRenderer = new PDFRenderer(srcDoc);

            Linearizer linearizer = new Linearizer(srcDoc.getDocument());
            WrittenObjectStore store = linearizer.linearize();
            FileOutputStream out = new FileOutputStream(new File(TARGETPDFDIR, file.getName()));
            store.writeFile(out);

            PDDocument targetDoc = PDDocument.load(new File(TARGETPDFDIR, file.getName()));

            PDFRenderer src2PdfRenderer = new PDFRenderer(targetDoc);
            for (int page = 0; page < pageCount; ++page)
            {
               BufferedImage img1 = src1PdfRenderer.renderImageWithDPI(page, DPI);
               BufferedImage img2 = src2PdfRenderer.renderImageWithDPI(page, DPI);
               checkImagesIdentical(img1, img2);
            }
         }
      }

   }

   private void checkImagesIdentical(BufferedImage bim1, BufferedImage bim2)
   {
      assertEquals(bim1.getHeight(), bim2.getHeight());
      assertEquals(bim1.getWidth(), bim2.getWidth());
      int w = bim1.getWidth();
      int h = bim1.getHeight();
      for (int i = 0; i < w; ++i)
      {
         for (int j = 0; j < h; ++j)
         {
            assertEquals(bim1.getRGB(i, j), bim2.getRGB(i, j));
         }
      }
   }

}
