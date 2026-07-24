package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.DomainModelVisualInfo;
import org.junit.Test;

public class MarkdownVisualOutputTest {

    @Test
    public void outputTest(){

        MarkdownVisualOutput markDownVisualOutput = new MarkdownVisualOutput();

        String output = markDownVisualOutput.output(new DomainModelVisualInfo());

        System.out.println(output);
    }
}
