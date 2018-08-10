package brave.internal;

import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.unmodifiableList;

public abstract class PropagationFieldsFactory<P extends PropagationFields> {
  protected abstract Class<P> type();

  protected abstract P create();

  protected abstract P create(P parent);

  public final TraceContext decorate(TraceContext context) {
    // If there's an implicit context while extracting only fields fields, we will have two extras!
    List<Object> extras = context.extra();
    int thisExtraIndex = -1, parentExtraIndex = -1;
    for (int i = 0, length = extras.size(); i < length; i++) {
      if (extras.get(i).getClass() == type()) {
        P fields = (P) context.extra().get(i);
        // If this fields is unassociated (due to remote extraction),
        // or it is the same span ID, re-use the instance.
        if (fields.tryAssociate(context)) {
          thisExtraIndex = i;
        } else {
          parentExtraIndex = i;
        }
      }
    }

    if (thisExtraIndex != -1 && parentExtraIndex == -1) return context;

    // otherwise, we are creating a new instance
    List<Object> copyOfExtra =
        extras.isEmpty() ? new ArrayList<>() : new ArrayList<>(context.extra());
    P fields;
    if (thisExtraIndex == -1 && parentExtraIndex != -1) { // clone then parent (for copy-on-write)
      fields = create((P) copyOfExtra.get(parentExtraIndex));
      copyOfExtra.set(parentExtraIndex, fields);
    } else if (thisExtraIndex != -1) { // merge with the parent
      fields = ((P) copyOfExtra.get(thisExtraIndex));
      P parent = (P) copyOfExtra.remove(parentExtraIndex); // ensures only one fields
      fields.putAllIfAbsent(parent); // extracted wins vs parent
    } else { // no fields were extracted and the parent also had no fields. create a new copy
      fields = create();
      copyOfExtra.add(fields);
    }
    TraceContext resultContext = context.toBuilder().extra(unmodifiableList(copyOfExtra)).build();
    fields.tryAssociate(resultContext); // associate this with the new context
    return resultContext;
  }
}