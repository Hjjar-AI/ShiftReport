"""Patient-card view state is independent of clinical ordering and exports."""
class PatientCards:
    def __init__(self):
        self.scope=None;self.pinned=set()

    def select(self,scope,ids):
        if scope!=self.scope:
            self.scope=scope;self.pinned.clear()
        self.pinned.intersection_update(ids)

    def toggle(self,key):
        if key in self.pinned:self.pinned.remove(key)
        else:self.pinned.add(key)
