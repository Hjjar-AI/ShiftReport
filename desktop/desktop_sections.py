"""Reusable RTL section shortcuts with 48px targets and retained page widgets."""
import desktop_widgets as ctk


class SectionPages(ctk.CTkFrame):
    def __init__(self,parent):
        super().__init__(parent,fg_color='transparent')
        self.navigation=ctk.CTkFrame(self,fg_color='transparent')
        self.navigation.pack(fill='x')
        self.body=ctk.CTkFrame(self,fg_color='transparent')
        self.body.pack(fill='both',expand=True)
        self.pages={};self.buttons={};self.selected=None;self.on_change=None
        self.bind('<Configure>',self.layout,add='+')

    def add(self,title,label=None):
        self.pages[title]=ctk.CTkFrame(self.body,fg_color='transparent')
        control=ctk.CTkButton(self.navigation,text=label or title,height=48,width=90,
            command=lambda:self.set(title))
        self.buttons[title]=(control,label or title)
        self.set(self.selected or title)
        self.layout()

    def tab(self,title):return self.pages[title]

    def get(self):return self.selected

    def set(self,title):
        if title not in self.pages:return
        for page in self.pages.values():page.pack_forget()
        self.pages[title].pack(fill='both',expand=True)
        self.selected=title
        for key,(control,label) in self.buttons.items():
            theme=ctk.ThemeManager.theme['CTkButton']
            active=key==title
            control.configure(text=label,
                fg_color=theme['fg_color'] if active else 'transparent',
                text_color=theme['text_color'] if active else ('gray20','gray90'),border_width=2 if active else 1)
        if self.on_change:self.on_change()

    def layout(self,event=None):
        if event is not None and event.widget is not self:return
        columns=4 if self.winfo_width()>=640 else 2
        for index in range(4):self.navigation.grid_columnconfigure(index,weight=1 if index<columns else 0)
        for index,(control,_) in enumerate(self.buttons.values()):
            control.grid_forget()
            control.grid(row=index//columns,column=columns-1-index%columns,sticky='ew',padx=3,pady=3)
