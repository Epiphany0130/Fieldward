package com.fieldward.jobs;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ApplicationMessageTest {
    @Test void acceptsOnlyExplicitSingleJobStatements() {
        var jobs = List.of(new JobMapper.JobView(1,"s","a","ACTIVE","Java 实习生","","https://example.com",null));
        assertThat(ApplicationMessage.parse("我已投递Java 实习生", jobs)).isEqualTo(new ApplicationMessage.Intent(1,"APPLIED"));
        assertThat(ApplicationMessage.parse("Java 实习生已经进入面试", jobs)).isEqualTo(new ApplicationMessage.Intent(1,"INTERVIEW"));
        assertThat(ApplicationMessage.parse("Java 实习生还没有进入面试", jobs)).isNull();
        assertThat(ApplicationMessage.parse("我可能会投递Java 实习生", jobs)).isNull();
        assertThat(ApplicationMessage.parse("我已投递", jobs)).isNull();
        assertThat(ApplicationMessage.parse("我已投递Java 实习生吗？", jobs)).isNull();
        assertThat(ApplicationMessage.parse("我已投递Java 实习生", List.of(jobs.getFirst(),jobs.getFirst()))).isNull();
    }
}
