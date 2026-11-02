"""Patient-card view state is independent of clinical ordering and exports."""
class PatientCards:
    def __init__(self):
        self.scope=None;self.pinned=set()
        self.expand_all=False;self.expansion={}

    def select(self,scope,ids):
        if scope!=self.scope:
            self.scope=scope;self.pinned.clear()
            self.expand_all=False;self.expansion.clear()
        self.pinned.intersection_update(ids)
        self.expansion={key:value for key,value in self.expansion.items() if key in ids}

    def toggle(self,key):
        if key in self.pinned:self.pinned.remove(key)
        else:self.pinned.add(key)

    def expanded(self,key):
        return self.expansion.get(key,self.expand_all)

    def toggle_expansion(self,key):
        self.expansion[key]=not self.expanded(key)

    def set_expanded(self,value):
        self.expand_all=bool(value);self.expansion.clear()
