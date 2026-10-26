Attribute VB_Name = "SRPdf"
Option Explicit
Private ws As Worksheet, measure As Worksheet, rowAt As Long, pageAt As Long, used As Double
Private currentPatient As String, currentPageTitle As String, pages As Long
Private pageAnchors As Object, indexLinks As Collection, navLinks As Collection, previews As Collection
Private PageBudget As Double
Private Const FontSize As Double = 12

Public Function PdfStyleSetting() As String
    PdfStyleSetting = UCase$(CStr(Sheet("AppHome").Range("B20").Value2))
    Need PdfStyleSetting = "CARDS" Or PdfStyleSetting = "CLASSIC" Or PdfStyleSetting = "PARAGRAPHS", "Select the PDF style on AppHome."
End Function

Public Function ReportInputKey() As String
    ReportInputKey = Digest(GridKey() & "|" & PdfStyleSetting())
End Function

Private Function WardSummary(ByVal shift As Object, ByVal supervisor As String) As String
    Dim p As Variant, task As Variant, tasks As Object, patients As Long, psychiatric As Long, companions As Long, pending As Long, overdue As Long
    For Each p In shift("patients")
        If Len(supervisor) = 0 Or Txt(p, "responsibleSupervisorId") = supervisor Then
            patients = patients + 1
            If Txt(p, "diagnosisType") = "PSYCHIATRIC" Or Txt(p, "diagnosisType") = "DUAL" Then psychiatric = psychiatric + 1
            If Txt(p, "hasCompanion") = "Y" Then companions = companions + 1
            Set tasks = ParseJson(Txt(p, "tasksJson", "[]"))
            For Each task In tasks
                If Not Flag(task, "done") Then
                    pending = pending + 1
                    If Num(task, "dueAtEpochMillis") > 0 And Num(task, "dueAtEpochMillis") <= EpochMs() Then overdue = overdue + 1
                End If
            Next task
        End If
    Next p
    WardSummary = Ui("patients") & ": " & patients & " | " & Ui("psychiatriccount") & ": " & psychiatric & " | " & Ui("companions") & ": " & companions & vbLf
    WardSummary = WardSummary & Ui("pendingcount") & ": " & pending & " (" & Ui("overduecount") & ": " & overdue & ")"
End Function

Public Function Ui(ByVal key As String) As String
    Dim labels As Worksheet, r As Long
    Set labels = Sheet("AppHelp")
    For r = 30 To LastContentRow(labels)
        If CStr(labels.Cells(r, 1).Value2) = key Then Ui = CStr(labels.Cells(r, 2).Value2): Exit Function
    Next r
    Ui = key
End Function

Private Function NameOf(ByVal doctors As Object, ByVal id As String) As String
    If doctors.Exists(id) Then NameOf = Txt(doctors(id), "name")
End Function

Public Function PatientSummary(ByVal p As Object, ByVal doctors As Object) As String
    Dim task As Variant, tasks As Object, text As String
    text = Txt(p, "name") & " | " & Ui("birth") & ": " & Txt(p, "birthDate") & " | " & GenderText(p)
    text = text & " | " & Ui(LCase$(Txt(p, "diagnosisType")))
    If Txt(p, "hasCompanion") = "Y" Then text = text & " | " & Ui("companion")
    If Txt(p, "isPriority") = "Y" Then text = text & " | " & Ui("high")
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

Private Function GenderText(ByVal p As Object) As String
    If Txt(p, "gender") = "F" Then GenderText = Ui("female") Else GenderText = Ui("male")
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
    If Len(Txt(task, "dueShiftDate")) Then text = text & " | " & Txt(task, "dueShiftDate")
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
    If PdfStyleSetting() = "CLASSIC" Then RenderPdf = RenderClassic(shift, doctors, supervisor): Exit Function
    If PdfStyleSetting() = "PARAGRAPHS" Then RenderPdf = RenderParagraphs(shift, doctors, supervisor): Exit Function
    PageBudget = 503
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
    ignored = AddBlock(WardSummary(shift, supervisor))
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
        header = Ui("birth") & ": " & Txt(patient, "birthDate") & " | " & GenderText(patient)
        header = header & " | " & Ui(LCase$(Txt(patient, "diagnosisType")))
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
        .PrintTitleRows = ""
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

Private Function RosterNames(ByVal shift As Object, ByVal doctors As Object) As String
    Dim id As Variant
    For Each id In Split(Txt(shift("meta"), "doctors"), ",")
        If Len(RosterNames) Then RosterNames = RosterNames & " / "
        RosterNames = RosterNames & NameOf(doctors, CStr(id))
    Next id
End Function

Private Function SavePdfSheet(ByVal lastColumn As String) As String
    With ws.PageSetup
        .PrintArea = "$A$1:$" & lastColumn & "$" & (rowAt - 1)
        .CenterFooter = "&P / &N": .PrintGridlines = False: .PrintHeadings = False
        .LeftHeader = "": .CenterHeader = "": .RightHeader = ""
    End With
    SavePdfSheet = Environ$("TEMP") & "\ShiftReportExcel-" & NewId() & ".pdf"
    ws.ExportAsFixedFormat Type:=xlTypePDF, Filename:=SavePdfSheet, Quality:=xlQualityStandard, IncludeDocProperties:=False, IgnorePrintAreas:=False, OpenAfterPublish:=False
    Need Len(Dir$(SavePdfSheet)) > 0, "Excel did not create the PDF."
    If previews Is Nothing Then Set previews = New Collection
    previews.Add SavePdfSheet: ws.Protect UserInterfaceOnly:=True
End Function

Private Function RenderParagraphs(ByVal shift As Object, ByVal doctors As Object, ByVal supervisor As String) As String
    Dim p As Variant, ignored As Long, i As Long, width As Double, count As Long
    Set ws = Sheet("AppPdf"): Set measure = Sheet("AppMeasure")
    ws.Unprotect: ws.Cells.UnMerge: ws.Cells.Clear: ws.ResetAllPageBreaks
    ws.DisplayRightToLeft = True: ws.Cells.Font.Name = "Tajawal"
    width = 595.276 - 17
    ws.Columns("A:H").ColumnWidth = 10
    For i = 1 To 4: ws.Columns("A:H").ColumnWidth = ws.Columns("A").ColumnWidth * width / ws.Range("A1:H1").Width: Next i
    measure.Columns("A").ColumnWidth = 75
    For i = 1 To 4: measure.Columns("A").ColumnWidth = measure.Columns("A").ColumnWidth * width / measure.Range("A1").Width: Next i
    PageBudget = 765: rowAt = 1: pages = 0: used = 0: currentPatient = "": Set navLinks = New Collection
    currentPageTitle = Txt(shift("meta"), "shiftDate"): StartPage currentPageTitle
    ignored = AddBlock(Ui("roster") & ": " & RosterNames(shift, doctors))
    ignored = AddBlock(WardSummary(shift, supervisor))
    For Each p In shift("patients")
        If Len(supervisor) = 0 Or Txt(p, "responsibleSupervisorId") = supervisor Then
            count = count + 1
            Section CStr(count) & ". " & Txt(p, "name"), PatientSummary(p, doctors)
        End If
    Next p
    Need count > 0, "No patients in the selected report."
    With ws.PageSetup
        .PaperSize = xlPaperA4: .Orientation = xlPortrait: .Zoom = 100
        .PrintTitleRows = ""
        .LeftMargin = 8.5: .RightMargin = 8.5: .TopMargin = 18: .BottomMargin = 35
        .HeaderMargin = 0: .FooterMargin = 12
    End With
    RenderParagraphs = SavePdfSheet("H")
End Function

Private Function TableHeight(ByVal text As String, ByVal width As Double, Optional ByVal size As Double = 9) As Double
    Dim i As Long
    measure.Columns("A").ColumnWidth = 20
    For i = 1 To 3: measure.Columns("A").ColumnWidth = measure.Columns("A").ColumnWidth * (width - 6) / measure.Range("A1").Width: Next i
    measure.Range("A1").NumberFormat = "@": measure.Range("A1").Value2 = text
    measure.Range("A1").Font.Name = "Tajawal": measure.Range("A1").Font.Size = size
    measure.Range("A1").WrapText = True: measure.Rows(1).AutoFit
    TableHeight = measure.Rows(1).RowHeight + 7
    If TableHeight < 18 Then TableHeight = 18
End Function

Private Function RenderClassic(ByVal shift As Object, ByVal doctors As Object, ByVal supervisor As String) As String
    Dim weights As Variant, widths(0 To 6) As Double, headers As Variant, content(0 To 6) As String, chunks(0 To 6) As String
    Dim p As Variant, tasks As Object, task As Variant, i As Long, count As Long, take As Long, height As Double, maximum As Double, more As Boolean
    Dim totalWidth As Double, names As String, days As Long, startDate As Date, headerRow As Long, chunk As String
    Set ws = Sheet("AppPdf"): Set measure = Sheet("AppMeasure")
    ws.Unprotect: ws.Cells.UnMerge: ws.Cells.Clear: ws.ResetAllPageBreaks
    ws.DisplayRightToLeft = True: ws.Cells.Font.Name = "Tajawal": ws.Cells.Font.Size = 9
    totalWidth = 841.89 - 17: weights = Array(4, 16, 13, 12, 15, 30, 34)
    headers = Array("#", Ui("patient"), Ui("diagnosis") & " / " & Ui("badges"), Ui("admission"), Ui("doctors"), Ui("treatment"), Ui("followup") & " / " & Ui("tasks") & " / " & Ui("labs"))
    For i = 0 To 6
        widths(i) = totalWidth * weights(i) / 124
        ws.Columns(i + 1).ColumnWidth = 15
        Dim pass As Long
        For pass = 1 To 3: ws.Columns(i + 1).ColumnWidth = ws.Columns(i + 1).ColumnWidth * widths(i) / ws.Cells(1, i + 1).Width: Next pass
    Next i
    names = RosterNames(shift, doctors)
    ws.Range("A1:G1").Merge: ws.Range("A1").NumberFormat = "@": ws.Range("A1").Value2 = Cfg("hospitalName") & " | " & Txt(shift("meta"), "shiftDate")
    ws.Range("A1:G1").WrapText = True: ws.Range("A1:G1").Font.Size = 11: ws.Rows(1).RowHeight = 32: rowAt = 2
    names = Ui("roster") & ": " & names
    Do While Len(names)
        take = Len(names): If take > 1000 Then take = 1000
        Do
            chunk = Left$(names, take): height = TableHeight(chunk, totalWidth, 11)
            If height <= 190 Then Exit Do
            take = take \ 2
        Loop
        With ws.Range(ws.Cells(rowAt, 1), ws.Cells(rowAt, 7))
            .Merge: .NumberFormat = "@": .Value2 = chunk: .WrapText = True: .Font.Size = 11
        End With
        ws.Rows(rowAt).RowHeight = height: rowAt = rowAt + 1: names = Mid$(names, take + 1)
    Loop
    With ws.Range(ws.Cells(rowAt, 1), ws.Cells(rowAt, 7))
        .Merge: .NumberFormat = "@": .Value2 = WardSummary(shift, supervisor): .WrapText = True
    End With
    ws.Rows(rowAt).RowHeight = 36: rowAt = rowAt + 1: headerRow = rowAt
    For i = 0 To 6: ws.Cells(rowAt, i + 1).Value2 = headers(i): Next i
    With ws.Range(ws.Cells(rowAt, 1), ws.Cells(rowAt, 7))
        .Font.Bold = True: .Interior.Color = RGB(227, 239, 245): .WrapText = True
    End With
    ws.Rows(rowAt).RowHeight = 32: rowAt = rowAt + 1
    For Each p In shift("patients")
        If Len(supervisor) = 0 Or Txt(p, "responsibleSupervisorId") = supervisor Then
            count = count + 1
            content(0) = CStr(count): content(1) = Txt(p, "name") & vbLf & Ui("birth") & ": " & Txt(p, "birthDate") & " | " & GenderText(p)
            If Txt(p, "hasCompanion") = "Y" Then content(1) = content(1) & vbLf & Ui("companion")
            content(2) = Txt(p, "initialDiagnosis") & BadgeText(p)
            If Txt(p, "isPriority") = "Y" Then content(2) = Ui("high") & vbLf & content(2)
            startDate = DateSerial(CInt(Left$(Txt(p, "admitDate"), 4)), CInt(Mid$(Txt(p, "admitDate"), 6, 2)), CInt(Right$(Txt(p, "admitDate"), 2)))
            days = DateDiff("d", startDate, DateSerial(CInt(Left$(Txt(shift("meta"), "shiftDate"), 4)), CInt(Mid$(Txt(shift("meta"), "shiftDate"), 6, 2)), CInt(Right$(Txt(shift("meta"), "shiftDate"), 2)))) + 1
            content(3) = Txt(p, "admitNum") & vbLf & Txt(p, "admitDate") & vbLf & CStr(days) & " " & Ui("days")
            content(4) = NameOf(doctors, Txt(p, "responsibleSupervisorId")) & vbLf & NameOf(doctors, Txt(p, "responsibleResidentId"))
            content(5) = Txt(p, "treatmentPlan"): content(6) = Txt(p, "followUp")
            Set tasks = ParseJson(Txt(p, "tasksJson", "[]"))
            For Each task In tasks: content(6) = content(6) & vbLf & Ui("tasks") & ": " & TaskText(task, doctors): Next task
            If Len(Txt(p, "labs")) Then content(6) = content(6) & vbLf & Ui("labs") & ": " & Txt(p, "labs")
            Do
                more = False: maximum = 18
                For i = 0 To 6
                    take = Len(content(i)): If take > 600 Then take = 600
                    Do
                        chunks(i) = Left$(content(i), take): height = TableHeight(chunks(i), widths(i))
                        If height <= 160 Or take <= 1 Then Exit Do
                        take = take \ 2
                    Loop
                    content(i) = Mid$(content(i), take + 1)
                    If Len(content(i)) Then more = True
                    If height > maximum Then maximum = height
                    ws.Cells(rowAt, i + 1).NumberFormat = "@": ws.Cells(rowAt, i + 1).Value2 = chunks(i)
                Next i
                ws.Range(ws.Cells(rowAt, 1), ws.Cells(rowAt, 7)).WrapText = True
                ws.Range(ws.Cells(rowAt, 1), ws.Cells(rowAt, 7)).VerticalAlignment = xlTop
                If count Mod 2 = 0 Then ws.Range(ws.Cells(rowAt, 1), ws.Cells(rowAt, 7)).Interior.Color = RGB(246, 249, 251)
                ws.Rows(rowAt).RowHeight = maximum: rowAt = rowAt + 1
            Loop While more
        End If
    Next p
    Need count > 0, "No patients in the selected report."
    With ws.PageSetup
        .PaperSize = xlPaperA4: .Orientation = xlLandscape: .Zoom = 100
        .LeftMargin = 8.5: .RightMargin = 8.5: .TopMargin = 18: .BottomMargin = 35
        .HeaderMargin = 0: .FooterMargin = 12: .PrintTitleRows = "$" & headerRow & ":$" & headerRow
    End With
    RenderClassic = SavePdfSheet("G")
End Function

Public Sub CleanupPreviews()
    Dim path As Variant
    If previews Is Nothing Then Exit Sub
    On Error Resume Next
    For Each path In previews: Kill CStr(path): Next path
    On Error GoTo 0
    Set previews = Nothing
End Sub
