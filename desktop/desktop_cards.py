"""Patient-card view state is independent of clinical ordering and exports."""
class PatientCards:
    def __init__(self):
        self.scope=None;self.pinned=set();self.expanded=set()

    def select(self,scope,ids):
        if scope!=self.scope:
            self.scope=scope;self.pinned.clear();self.expanded.clear()
        self.pinned.intersection_update(ids);self.expanded.intersection_update(ids)

    def toggle(self,key,pin=False):
        values=self.pinned if pin else self.expanded
        if key in values:values.remove(key)
        else:values.add(key)

    def expand_all(self,ids,expand):
        self.expanded=set(ids) if expand else set()
