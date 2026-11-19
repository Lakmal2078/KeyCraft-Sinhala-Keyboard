package com.lakmal.keycraftsinhala;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SinhalaInputHandlerTest
{
  @Test
  public void deletesWholeSinhalaYansayaGrapheme()
  {
    assertEquals(4, SinhalaInputHandler.calculateGraphemeCodeUnits("ක්‍ර", 1));
  }

  @Test
  public void deletesWholeSinhalaRepayaGrapheme()
  {
    assertEquals(4, SinhalaInputHandler.calculateGraphemeCodeUnits("ර්‍ම", 1));
  }

  @Test
  public void preservesPreviousGlyphWhenDeletingConjunct()
  {
    assertEquals(4, SinhalaInputHandler.calculateGraphemeCodeUnits("අක්‍ර", 1));
    assertEquals(5, SinhalaInputHandler.calculateGraphemeCodeUnits("අක්‍ර", 2));
  }

  @Test
  public void countsOrdinarySinhalaGraphemes()
  {
    assertEquals(2, SinhalaInputHandler.calculateGraphemeCodeUnits("කෙ", 1));
  }
}
