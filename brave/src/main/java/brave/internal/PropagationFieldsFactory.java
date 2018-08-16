package brave.internal;

import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static brave.internal.Lists.ensureMutable;

public abstract class PropagationFieldsFactory<P extends PropagationFields> {
  protected abstract Class<P> type();

  protected abstract P create();

  protected abstract P create(P parent);

  public final TraceContext decorate(TraceContext context) {
    long traceId = context.traceId(), spanId = context.spanId();
    Class<P> type = type();

    List<Object> extraFields = context.extra();
    int extraSize = extraFields.size();
    if (extraSize == 0) {
      extraFields = Collections.singletonList(createExtraAndClaim(traceId, spanId));
      return contextWithExtra(context, extraFields);
    }

    Object extra = extraFields.get(0);
    P consolidatedFields = null;

    // if the first item is a fields object, try to claim or copy its fields
    if (type.isInstance(extra)) {
      P existing = (P) extra;
      if (existing.tryToClaim(traceId, spanId)) {
        consolidatedFields = existing;
      } else { // otherwise we need to consolidate the fields
        consolidatedFields = createExtraAndClaim(traceId, spanId);
        consolidatedFields.putAllIfAbsent(existing);
      }
    }

    // If we had only one extra, there are a few options:
    // * we claimed an existing fields object successfully
    // * we copied existing fields into a new fields object claimed by this ID
    // * the existing extra was not a fields object, so we need to make a new list
    if (extraSize == 1) {
      if (consolidatedFields != null) {
        if (consolidatedFields == extra) return context;
        // otherwise we copied the fields of an existing object
        return contextWithExtra(context, Collections.singletonList(consolidatedFields));
      }
      // we need to make new list to hold the unrelated extra element and our fields
      extraFields = new ArrayList<>(2);
      extraFields.add(extra);
      extraFields.add(createExtraAndClaim(traceId, spanId));
      return contextWithExtra(context, Collections.unmodifiableList(extraFields));
    }

    // If we get here, we have at least one extra, but don't yet know if we need to create
    // a new list. For example, if there is an unassociated fields object we may be able to
    // avoid creating a new list.
    for (int i = 1; i < extraSize; i++) {
      extra = extraFields.get(i);
      if (!type.isInstance(extra)) continue;
      P existing = (P) extra;
      if (consolidatedFields == null) {
        if (existing.tryToClaim(traceId, spanId)) {
          consolidatedFields = existing;
          continue;
        }
        consolidatedFields = createExtraAndClaim(traceId, spanId);
        consolidatedFields.putAllIfAbsent(existing);
        extraFields = ensureMutable(extraFields);
        extraFields.set(i, consolidatedFields);
      } else {
        consolidatedFields.putAllIfAbsent(existing);
        extraFields = ensureMutable(extraFields);
        extraFields.remove(i); // drop the previous fields item as we consolidated it
        extraSize--;
        i--;
      }
    }
    if (consolidatedFields == null) {
      consolidatedFields = createExtraAndClaim(traceId, spanId);
      extraFields = ensureMutable(extraFields);
      extraFields.add(consolidatedFields);
    }
    if (extraFields == context.extra()) return context;
    return contextWithExtra(context, Collections.unmodifiableList(extraFields));
  }

  P createExtraAndClaim(long traceId, long spanId) {
    P consolidatedFields = create();
    consolidatedFields.tryToClaim(traceId, spanId);
    return consolidatedFields;
  }

  protected TraceContext contextWithExtra(TraceContext context, List<Object> extra) {
    return TraceContexts.contextWithExtra(context, extra);
  }
}
