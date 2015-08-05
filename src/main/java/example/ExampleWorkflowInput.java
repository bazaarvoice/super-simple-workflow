package example;

import com.bazaarvoice.sswf.InputParser;

public class ExampleWorkflowInput {
    /**
     * Realistically, you'd use Jackson or something for the implementation.
     */
    public static final class Parser implements InputParser<ExampleWorkflowInput> {
        @Override public String serialize(final ExampleWorkflowInput exampleWorkflowInput) {
            return "name:"+ exampleWorkflowInput.getName();
        }

        @Override public ExampleWorkflowInput deserialize(final String inputString) {
            return new ExampleWorkflowInput(inputString.split(":")[1]);
        }
    }

    final private String name;

    public ExampleWorkflowInput(final String name) {this.name = name;}

    public String getName() {
        return name;
    }
}
