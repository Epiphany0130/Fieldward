package com.fieldward.mentor;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MentorApplicationInstructionTest {
    @Test void ordinaryModelCannotClaimApplicationWasUpdated() {
        assertThat(MentorModel.INSTRUCTIONS).contains("不得声称已经更新投递进度");
    }
}
