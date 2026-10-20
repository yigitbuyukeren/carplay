package com.ridedeck.app

import com.ridedeck.app.data.InstructionText
import org.junit.Assert.assertEquals
import org.junit.Test

class InstructionTextTest {
    @Test fun doubledRoadTypeIsMergedKeepingTheSuffix() =
        assertEquals("Ahmet Yesevi Caddesine doğru sola dönün.", InstructionText.clean("Ahmet Yesevi Caddesi caddesine doğru sola dönün."))

    @Test fun sokakIsMergedToo() =
        assertEquals("Gül Sokağına dönün", InstructionText.clean("Gül Sokağı sokağına dönün"))

    @Test fun twoDifferentRoadsStayAsTheyAre() =
        assertEquals("Atatürk Caddesi Gül Sokağına dönün", InstructionText.clean("Atatürk Caddesi Gül Sokağına dönün"))

    @Test fun plainTextIsUntouched() =
        assertEquals("Sağa dönün.", InstructionText.clean("Sağa dönün."))
}
