package brave.internal;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PredefinedPropagationFieldsTest
    extends PropagationFieldsFactoryTest<PredefinedPropagationFields> {
  @Override protected PropagationFieldsFactory newFactory() {
    return new PropagationFieldsFactory<PredefinedPropagationFields>() {
      @Override public Class<PredefinedPropagationFields> type() {
        return PredefinedPropagationFields.class;
      }

      @Override public PredefinedPropagationFields create() {
        return new PredefinedPropagationFields(FIELD1, FIELD2);
      }

      @Override protected PredefinedPropagationFields create(PredefinedPropagationFields parent) {
        return new PredefinedPropagationFields(parent, FIELD1, FIELD2);
      }
    };
  }

  @Test public void put_ignore_if_not_defined() {
    PropagationFields.put(context, "balloon-color", "red", factory.type());

    assertThat(((PropagationFields) context.extra().get(0)).toMap())
        .isEmpty();
  }

  @Test public void put_ignore_if_not_defined_index() {
    PredefinedPropagationFields fields = factory.create();

    fields.put(4, "red");

    assertThat(fields)
        .isEqualToComparingFieldByField(factory.create());
  }

  @Test public void put_idempotent() {
    PredefinedPropagationFields fields = factory.create();

    fields.put("foo", "red");
    String[] fieldsArray = fields.values;

    fields.put("foo", "red");
    assertThat(fields.values)
        .isSameAs(fieldsArray);

    fields.put("foo", "blue");
    assertThat(fields.values)
        .isNotSameAs(fieldsArray);
  }

  @Test public void get_ignore_if_not_defined_index() {
    PredefinedPropagationFields fields = factory.create();

    assertThat(fields.get(4))
        .isNull();
  }

  @Test public void toMap_one_index() {
    PredefinedPropagationFields fields = factory.create();
    fields.put(1, "a");

    assertThat(fields.toMap())
        .hasSize(1)
        .containsEntry(FIELD2, "a");
  }

  @Test public void toMap_two_index() {
    PredefinedPropagationFields fields = factory.create();
    fields.put(0, "1");
    fields.put(1, "a");

    assertThat(fields.toMap())
        .hasSize(2)
        .containsEntry(FIELD1, "1")
        .containsEntry(FIELD2, "a");
  }
}
