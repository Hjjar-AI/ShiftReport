Attribute VB_Name = "SRPdf"
Option Explicit
Private ws As Worksheet, measure As Worksheet, rowAt As Long, pageAt As Long, used As Double
Private currentPatient As String, currentPageTitle As String, pages As Long
Private pageAnchors As Object, indexLinks As Collection, navLinks As Collection, previews As Collection
Private Const PageBudget As Double = 503
Private Const FontSize As Double = 12

Public Function Ui(ByVal key As String) As String
    Dim labels As Worksheet, r As Long
    Set labels = Sheet("AppHelp")
    For r = 30 To 70
        If CStr(labels.Cells(r, 1).Value2) = key Then Ui = CStr(labels.Cells(r, 2).Value2): Exit Function
    Next r
    Ui = key
End Function

Private Function NameOf(ByVal doctors As Object, ByVal id As String) As String
    If doctors.Exists(id) Then NameOf = Txt(doctors(id), "name")
End Function

Public Function PatientSummary(ByVal p As Object, ByVal doctors As Object) As String
    Dim task As Variant, tasks As Object, text As String
    text = Txt(p, "name") & " | " & Ui("birth") & ": " & Txt(p, "birthDate") & " | " & Txt(p, "gender")
    text = text & vbLf & Ui("admission") & ": " & Txt(p, "admitNum") & " / " & Txt(p, "admitDate")
    text = text & vbLf & Ui("diagnosis") & ": " & Txt(p, "initialDiagnosis")
    text = text & vbLf & Ui("treatment") & ": " & Txt(p, "treatmentPlan")
    text = text & vbLf & Ui("followup") & ": " & Txt(p, "followUp")
    If Len(Txt(p, "labs")) Then text = text & vbLf & Ui("labs") & ": " & Txt(p, "labs")
    If Len(Txt(p, "responsibleSupervisorId")) Then text = text & vbLf & Ui("supervisor") & ": " & NameOf(doctors, Txt(p, "responsibleSupervisorId"))
    If Len(Txt(p, "responsibleResidentId")) Then text = text & vbLf & Ui("resident") & ": " & NameOf(doctors, Txt(p, "responsibleResidentId"))
    Set tasks = ParseJson(Txt(p, "tasksJson", "[]"))
    For Each task In tasks: text = text & vbLf & Ui("tasks") & ": " & TaskText(task, doctors): Next task
    PatientSummary = text & BadgeText(p)
End Function

Private Function BadgeText(ByVal p As Object) As String
    Dim text As String, entries As Variant, item As Variant, separator As Long, encoded As String, raw() As Byte, level As String
    text = Txt(p, "warningDetails"): If Len(text) = 0 Then Exit Function
    Need Left$(text, 7) = "badges:", "Unsupported badge encoding."
    entries = Split(Mid$(text, 8), ";")
    For Each item In entries
        separator = InStr(CStr(item), ":"): Need separator > 1, "Invalid badge entry."
        level = Left$(item, separator - 1): encoded = Mid$(item, separator + 1)
        encoded = Replace(Replace(encoded, "-", "+"), "_", "/")
        Do While Len(encoded) Mod 4: encoded = encoded & "=": Loop
        raw = Unbase64(encoded): text = TextUtf8(raw)
        BadgeText = BadgeText & vbLf & text
        If level <> "none" Then BadgeText = BadgeText & " (" & level & ")"
    Next item
End Function

Private Function TaskText(ByVal task As Object, ByVal doctors As Object) As String
    Dim text As String, owner As String, status As String, due As Double
    If Flag(task, "done") Then status = Ui("done") Else status = Ui("pending")
    text = status & " - " & Txt(task, "description")
    If Txt(task, "priority", "NORMAL") = "HIGH" Then text = text & " | " & Ui("high")
    owner = NameOf(doctors, Txt(task, "ownerDoctorId"))
    If Len(owner) = 0 Then owner = Txt(task, "ownerName")
    If Len(owner) Then text = text & " | " & owner
    due = Num(task, "dueAtEpochMillis")
    If due > 0 Then text = text & " | " & Ui("due") & ": " & Format$(#1/1/1970# + (due + 10800000#) / 86400000#, "yyyy-mm-dd hh:nn")
    If Flag(task, "done") Then
        text = text & " | " & Txt(task, "completedByName") & " " & Format$(#1/1/1970# + (Num(task, "completedAtEpochMillis") + 10800000#) / 86400000#, "yyyy-mm-dd hh:nn")
    End If
    TaskText = text
End Function

Private Sub MergedText(ByVal first As Long, ByVal last As Long, ByVal text As String, ByVal height As Double, Optional ByVal bold As Boolean = False)
    Dim target As Range: Set target = ws.Range(ws.Cells(rowAt, first), ws.Cells(rowAt, last))
    target.Merge: target.NumberFormat = "@": target.Value2 = text
    target.Font.Name = "Tajawal": target.Font.Size = FontSize: target.Font.Bold = bold
    target.WrapText = True: target.HorizontalAlignment = xlRight: target.VerticalAlignment = xlTop
    ws.Rows(rowAt).RowHeight = height
End Sub

Private Function HeightFor(ByVal text As String, ByVal bold As Boolean) As Double
    measure.Range("A1").NumberFormat = "@": measure.Range("A1").Value2 = text
    measure.Range("A1").Font.Name = "Tajawal": measure.Range("A1").Font.Size = FontSize
    measure.Range("A1").Font.Bold = bold: measure.Range("A1").WrapText = True
    measure.Rows(1).AutoFit: HeightFor = measure.Rows(1).RowHeight + 5
    If HeightFor < 22 Then HeightFor = 22
End Function

Private Sub StartPage(ByVal title As String)
    Dim gap As Double, nav As Object
    If rowAt > 1 Then
        gap = PageBudget - used
        Do While gap > 0
            ws.Rows(rowAt).RowHeight = IIf(gap > 300, 300, gap): gap = gap - ws.Rows(rowAt).RowHeight: rowAt = rowAt + 1
        Loop
        ws.HPageBreaks.Add Before:=ws.Cells(rowAt, 1)
    End If
    pageAt = rowAt: pages = pages + 1: used = 0
    MergedText 1, 8, Cfg("hospitalName"), 25, True: rowAt = rowAt + 1: used = used + 25
    MergedText 1, 8, title, 27, True: rowAt = rowAt + 1: used = used + 27
    If Len(currentPatient) Then
        Set nav = Dict(): Put nav, "row", rowAt: Put nav, "patient", currentPatient: navLinks.Add nav
        MergedText 1, 3, Ui("index"), 22: MergedText 4, 6, Ui("previous"), 22: MergedText 7, 8, Ui("next"), 22
        rowAt = rowAt + 1: used = used + 22
    End If
End Sub

Private Function AddBlock(ByVal text As String, Optional ByVal bold As Boolean = False, Optional ByVal fill As Long = -1) As Long
    Dim remaining As String, chunk As String, count As Long, breakAt As Long, height As Double
    remaining = Replace(text, vbCrLf, vbLf)
    If Len(remaining) = 0 Then Exit Function
    Do While Len(remaining)
        count = Len(remaining): If count > 900 Then count = 900
        Do
            chunk = Left$(remaining, count): height = HeightFor(chunk, bold)
            If height <= 190 Or count <= 1 Then Exit Do
            count = count \ 2
        Loop
        If count < Len(remaining) Then
            breakAt = InStrRev(chunk, vbLf): If breakAt < count \ 2 Then breakAt = InStrRev(chunk, " ")
            If breakAt > count \ 2 Then count = breakAt: chunk = Left$(remaining, count): height = HeightFor(chunk, bold)
        End If
        If used + height > PageBudget Then StartPage currentPageTitle & " - " & Ui("continued")
        If AddBlock = 0 Then AddBlock = rowAt
        MergedText 1, 8, chunk, height, bold
        If fill <> -1 Then ws.Range(ws.Cells(rowAt, 1), ws.Cells(rowAt, 8)).Interior.Color = fill
        rowAt = rowAt + 1: used = used + height: remaining = Mid$(remaining, count + 1)
    Loop
End Function

Private Sub Section(ByVal label As String, ByVal content As String)
    Dim ignored As Long
    If Len(Trim$(content)) = 0 Then Exit Sub
    'Reserve label + at least one line together.
    If used + 60 > PageBudget Then StartPage currentPageTitle & " - " & Ui("continued")
    ignored = AddBlock(label, True, RGB(240, 245, 248))
    ignored = AddBlock(content)
End Sub

Public Function RenderPdf(ByVal shift As Object, ByVal doctors As Object, ByVal supervisor As String) As String
    Dim patient As Variant, selected As New Collection, key As Variant, names As String, header As String, ignored As Long
    Dim tasks As Object, task As Variant, text As String, item As Object, anchor As Long, position As Long, entry As Object, i As Long
    Dim width As Double, title As String, links As Variant, previous As String, following As String
    Set ws = Sheet("AppPdf"): Set measure = Sheet("AppMeasure")
    ws.Unprotect: ws.Cells.UnMerge: ws.Cells.Clear: ws.ResetAllPageBreaks
    ws.DisplayRightToLeft = True: ws.Cells.Font.Name = "Tajawal": ws.Cells.Font.Size = FontSize
    ws.Columns("A:H").ColumnWidth = 6: width = 420# - 2 * 15# * 72# / 25.4
    For i = 1 To 4: ws.Columns("A:H").ColumnWidth = ws.Columns("A").ColumnWidth * width / ws.Range("A1:H1").Width: Next i
    measure.Columns("A").ColumnWidth = 50
    For i = 1 To 4: measure.Columns("A").ColumnWidth = measure.Columns("A").ColumnWidth * width / measure.Range("A1").Width: Next i
    Set pageAnchors = Dict(): Set indexLinks = New Collection: Set navLinks = New Collection
    rowAt = 1: pages = 0: used = 0: currentPatient = ""
    For Each patient In shift("patients")
        If Len(supervisor) = 0 Or Txt(patient, "responsibleSupervisorId") = supervisor Then selected.Add patient
    Next patient
    Need selected.Count > 0, "No patients in the chosen PDF scope."
    For Each key In Split(Txt(shift("meta"), "doctors"), ",")
        If Len(names) Then names = names & " / "
        names = names & NameOf(doctors, CStr(key))
    Next key
    title = Ui("index") & " | " & Txt(shift("meta"), "shiftDate"): currentPageTitle = title
    StartPage title: ignored = AddBlock(Ui("roster") & ": " & names)
    For Each patient In selected
        Set entry = Dict(): Put entry, "patient", Txt(patient, "externalId")
        anchor = AddBlock(CStr(indexLinks.Count + 1) & ". " & Txt(patient, "name"))
        Put entry, "row", anchor: indexLinks.Add entry
    Next patient
    position = 0
    For Each patient In selected
        position = position + 1: currentPatient = Txt(patient, "externalId")
        currentPageTitle = Txt(patient, "name") & " | " & Txt(shift("meta"), "shiftDate")
        StartPage currentPageTitle: Put pageAnchors, currentPatient, pageAt
        ignored = AddBlock(Ui("roster") & ": " & names)
        header = Ui("birth") & ": " & Txt(patient, "birthDate") & " | " & Txt(patient, "gender")
        If Txt(patient, "hasCompanion") = "Y" Then header = header & " | " & Ui("companion")
        If Txt(patient, "isPriority") = "Y" Then header = header & " | " & Ui("high")
        ignored = AddBlock(header)
        Section Ui("admission"), Txt(patient, "admitNum") & " | " & Txt(patient, "admitDate")
        Section Ui("badges"), Trim$(BadgeText(patient))
        Section Ui("diagnosis"), Txt(patient, "initialDiagnosis")
        Section Ui("treatment"), Txt(patient, "treatmentPlan")
        Section Ui("followup"), Txt(patient, "followUp")
        Section Ui("labs"), Txt(patient, "labs")
        text = ""
        If Len(Txt(patient, "responsibleSupervisorId")) Then text = Ui("supervisor") & ": " & NameOf(doctors, Txt(patient, "responsibleSupervisorId"))
        If Len(Txt(patient, "responsibleResidentId")) Then text = text & vbLf & Ui("resident") & ": " & NameOf(doctors, Txt(patient, "responsibleResidentId"))
        Section Ui("doctors"), Trim$(text)
        text = "": Set tasks = ParseJson(Txt(patient, "tasksJson", "[]"))
        For Each task In tasks
            If Len(text) Then text = text & vbLf
            text = text & TaskText(task, doctors)
        Next task
        Section Ui("tasks"), text
    Next patient
    For Each entry In indexLinks
        anchor = CLng(entry("row"))
        ws.Hyperlinks.Add Anchor:=ws.Cells(anchor, 1), Address:="", SubAddress:="'AppPdf'!A" & pageAnchors(Txt(entry, "patient")), TextToDisplay:=CStr(ws.Cells(anchor, 1).Value2)
    Next entry
    For Each entry In navLinks
        anchor = CLng(entry("row")): position = 0
        For i = 1 To selected.Count
            If Txt(selected(i), "externalId") = Txt(entry, "patient") Then position = i: Exit For
        Next i
        ws.Hyperlinks.Add Anchor:=ws.Cells(anchor, 1), Address:="", SubAddress:="'AppPdf'!A1", TextToDisplay:=Ui("index")
        If position > 1 Then ws.Hyperlinks.Add Anchor:=ws.Cells(anchor, 4), Address:="", SubAddress:="'AppPdf'!A" & pageAnchors(Txt(selected(position - 1), "externalId")), TextToDisplay:=Ui("previous")
        If position < selected.Count Then ws.Hyperlinks.Add Anchor:=ws.Cells(anchor, 7), Address:="", SubAddress:="'AppPdf'!A" & pageAnchors(Txt(selected(position + 1), "externalId")), TextToDisplay:=Ui("next")
    Next entry
    With ws.PageSetup
        .PaperSize = xlPaperA5: .Orientation = xlPortrait
        .LeftMargin = Application.CentimetersToPoints(1.5): .RightMargin = Application.CentimetersToPoints(1.5)
        .TopMargin = Application.CentimetersToPoints(2): .BottomMargin = Application.CentimetersToPoints(1)
        .HeaderMargin = 0: .FooterMargin = Application.CentimetersToPoints(0.35)
        .Zoom = 100: .PrintArea = "$A$1:$H$" & rowAt - 1
        .CenterFooter = "&P / &N": .PrintGridlines = False: .PrintHeadings = False
        .LeftHeader = "": .CenterHeader = "": .RightHeader = ""
    End With
    RenderPdf = Environ$("TEMP") & "\ShiftReportExcel-" & NewId() & ".pdf"
    ws.ExportAsFixedFormat Type:=xlTypePDF, Filename:=RenderPdf, Quality:=xlQualityStandard, IncludeDocProperties:=False, IgnorePrintAreas:=False, OpenAfterPublish:=False
    Need Len(Dir$(RenderPdf)) > 0, "Excel did not create the PDF."
    If previews Is Nothing Then Set previews = New Collection
    previews.Add RenderPdf
    ws.Protect UserInterfaceOnly:=True
End Function

Public Sub CleanupPreviews()
    Dim path As Variant
    If previews Is Nothing Then Exit Sub
    On Error Resume Next
    For Each path In previews: Kill CStr(path): Next path
    On Error GoTo 0
    Set previews = Nothing
End Sub
