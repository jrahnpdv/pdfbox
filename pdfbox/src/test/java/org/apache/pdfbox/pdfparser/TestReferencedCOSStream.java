/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.pdfbox.pdfparser;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author Jonathan Rahn
 */
public class TestReferencedCOSStream
{
   
   final String SRCDIR = "src/test/resources/input/merge/";
   
   @Test
   public void testReferencedStream() throws IOException
   {
      int DPI = 50;
      String filename1 = "jpegrgb.pdf";
      int pageCount;
      BufferedImage[] src1ImageTab;
      BufferedImage[] src2ImageTab;

      try (PDDocument srcDoc1 = PDDocument.load(new FileInputStream(new File(SRCDIR, filename1)));
              PDDocument srcDoc2 = PDDocument.load(new File(SRCDIR, filename1)) 
              )
      {
         pageCount = srcDoc2.getNumberOfPages();
         PDFRenderer src1PdfRenderer = new PDFRenderer(srcDoc1);
         PDFRenderer src2PdfRenderer = new PDFRenderer(srcDoc2);
         src2ImageTab = new BufferedImage[pageCount];
         src1ImageTab = new BufferedImage[pageCount];
         for (int page = 0; page < pageCount; ++page)
         {
            src1ImageTab[page] = src1PdfRenderer.renderImageWithDPI(page, DPI);
            src2ImageTab[page] = src2PdfRenderer.renderImageWithDPI(page, DPI);
         }
      }

      for (int page = 0; page < pageCount; ++page)
      {
         checkImagesIdentical(src1ImageTab[page], src2ImageTab[page]);
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
