Attribute VB_Name = "SRWorkbook"
Option Explicit

Public Function Sheet(ByVal name As String) As Worksheet
    Set Sheet = ThisWorkbook.Worksheets(name)
End Function

Public Function StateText(ByVal key As String) As String
    Dim ws As Worksheet, r As Long
    Set ws = Sheet("AppState")
    For r = 2 To ws.Cells(ws.Rows.Count, 1).End(xlUp).Row
        If CStr(ws.Cells(r, 1).Value2) = key Then StateText = StateText & CStr(ws.Cells(r, 3).Value2)
    Next r
End Function

Public Sub StateSet(ByVal key As String, ByVal value As String)
    Dim ws As Worksheet, r As Long, chunk As Long, i As Long
    Set ws = Sheet("AppState"): ws.Unprotect
    For r = ws.Cells(ws.Rows.Count, 1).End(xlUp).Row To 2 Step -1
        If CStr(ws.Cells(r, 1).Value2) = key Then ws.Rows(r).Delete
    Next r
    r = ws.Cells(ws.Rows.Count, 1).End(xlUp).Row + 1
    For i = 1 To Len(value) Step 30000
        ws.Cells(r, 1).NumberFormat = "@": ws.Cells(r, 3).NumberFormat = "@"
        ws.Cells(r, 1).Value2 = key: ws.Cells(r, 2).Value2 = chunk: ws.Cells(r, 3).Value2 = Mid$(value, i, 30000)
        chunk = chunk + 1: r = r + 1
    Next i
    ws.Protect UserInterfaceOnly:=True: ws.Visible = xlSheetVeryHidden
End Sub

Public Sub SaveLocalWorkbook()
    Need Not ThisWorkbook.ReadOnly, "Workbook is read-only; save an editable local .xlsm copy first."
    Need (LCase$(Right$(ThisWorkbook.Name, 5)) = ".xlsm" Or LCase$(Right$(ThisWorkbook.Name, 4)) = ".xls") And Len(ThisWorkbook.Path) > 0, "Save the prepared workbook as .xlsm (or .xls) before using these macros."
    ThisWorkbook.Save
    Need ThisWorkbook.Saved, "Cannot persist local state; no network write was attempted."
End Sub

Public Sub SnapshotBackup()
    Dim path As String
    Need Len(ThisWorkbook.Path) > 0, "Save a local workbook first."
    path = ThisWorkbook.Path & Application.PathSeparator & "before-retrieve-" & NewId()
    If LCase$(Right$(ThisWorkbook.Name, 4)) = ".xls" Then path = path & ".xls" Else path = path & ".xlsm"
    ThisWorkbook.SaveCopyAs path
End Sub

Public Function GridKey() As String
    Dim ws As Worksheet, r As Long, c As Long, text As String, names As Variant, name As Variant, last As Long, values As Variant, width As Long
    names = Array("Data", "AppDoctors", "AppTasks", "AppBadges")
    For Each name In names
        Set ws = Sheet(CStr(name)): last = LastContentRow(ws)
        Need last <= 10000, "Worksheet exceeds the supported editor range."
        width = IIf(CStr(name) = "Data", 20, 11)
        values = ws.Range(ws.Cells(1, 1), ws.Cells(last, width)).Value2
        text = text & CStr(name) & vbLf
        For r = 1 To last
            For c = 1 To width
                'Calculated counts/age/admission-days do not invalidate a review.
                If CStr(name) <> "Data" Or (r >= 6 And c <> 1 And c <> 3 And c <> 16 And c <> 19) Or (r = 1 And c = 2) Then
                    Need Not IsError(values(r, c)), "A workbook cell contains an error."
                    text = text & Quote(CStr(values(r, c))) & ","
                End If
            Next c
            text = text & vbLf
        Next r
    Next name
    GridKey = Digest(text)
End Function

Public Function LastContentRow(ByVal ws As Worksheet) As Long
    Dim last As Range
    Set last = ws.Cells.Find(What:="*", After:=ws.Cells(1, 1), LookIn:=xlFormulas, LookAt:=xlPart, SearchOrder:=xlByRows, SearchDirection:=xlPrevious, MatchCase:=False, SearchFormat:=False)
    If last Is Nothing Then LastContentRow = 1 Else LastContentRow = last.Row
End Function

Public Sub ShowDoctors(ByVal doctors As Object, Optional ByVal roster As String = "__KEEP__")
    Dim ws As Worksheet, key As Variant, r As Long, d As Object, selected As String
    Set ws = Sheet("AppDoctors"): ws.Unprotect
    If roster = "__KEEP__" Then
        roster = ""
        For r = 2 To ws.Cells(ws.Rows.Count, 1).End(xlUp).Row
            If CStr(ws.Cells(r, 4).Value2) = "Y" Then roster = roster & IIf(Len(roster), ",", "") & CStr(ws.Cells(r, 1).Value2)
        Next r
    End If
    ws.Range("A2:H10000").ClearContents
    ws.Range("A2:H10000").NumberFormat = "@": r = 2
    For Each key In doctors.Keys
        Set d = doctors(key): selected = "N"
        If InStr(1, "," & roster & ",", "," & CStr(key) & ",", vbBinaryCompare) > 0 Then selected = "Y"
        ws.Cells(r, 1).Value2 = CStr(key): ws.Cells(r, 2).Value2 = Txt(d, "name")
        ws.Cells(r, 3).Value2 = Txt(d, "clinicalRole"): ws.Cells(r, 4).Value2 = selected
        ws.Cells(r, 5).Value2 = Txt(d, "name") & " [" & CStr(key) & "]"
        ws.Cells(r, 6).Value2 = Txt(d, "teamSupervisorId")
        'Destinations and Telegram identities stay in the in-memory live registry.
        r = r + 1
    Next key
    ws.Cells.Locked = True: ws.Range("D2:D" & r).Locked = False
    ws.Protect UserInterfaceOnly:=True, AllowFiltering:=True
End Sub

Public Function DoctorLabel(ByVal doctors As Object, ByVal id As String) As String
    If Len(id) = 0 Then Exit Function
    Need doctors.Exists(id), "A patient assignment references an unavailable doctor. Correct it using AppDoctors."
    DoctorLabel = Txt(doctors(id), "name") & " [" & id & "]"
End Function

Public Function ResolveDoctor(ByVal text As String, ByVal role As String, ByVal doctors As Object) As String
    Dim key As Variant, matches As Long, candidate As String
    text = Trim$(text): If Len(text) = 0 Then Exit Function
    For Each key In doctors.Keys
        If Txt(doctors(key), "clinicalRole") = role Then
            If text = CStr(key) Or text = Txt(doctors(key), "name") Or text = DoctorLabel(doctors, CStr(key)) Then
                matches = matches + 1: candidate = CStr(key)
            End If
        End If
    Next key
    Need matches = 1, "Doctor name is missing/ambiguous or has the wrong role. Use the exact UUID or label from AppDoctors."
    ResolveDoctor = candidate
End Function

Private Sub SetText(ByVal cell As Range, ByVal value As String)
    Need Len(value) <= 32767, "Content exceeds Excel's per-cell limit; it was not truncated."
    cell.NumberFormat = "@": cell.Value2 = value
End Sub

Public Sub WriteShift(ByVal shift As Object, ByVal doctors As Object)
    Dim ws As Worksheet, meta As Object, p As Variant, r As Long, tasks As Object, t As Variant, tr As Long, br As Long
    Dim raw() As Byte, badges As Variant, badge As Variant, pair As Long, badgeText As String, roster As Variant, i As Long
    CheckGridShift shift, doctors
    Set ws = Sheet("Data"): ws.Unprotect: ws.Range("A6:T1000").ClearContents
    Set meta = shift("meta"): SetText ws.Range("B1"), Txt(meta, "shiftDate")
    ws.Range("D1:F1").ClearContents: roster = Split(Txt(meta, "doctors"), ",")
    For i = 0 To UBound(roster)
        If i > 2 Then Exit For
        If doctors.Exists(roster(i)) Then SetText ws.Cells(1, 4 + i), Txt(doctors(roster(i)), "name")
    Next i
    Sheet("AppTasks").Unprotect: Sheet("AppBadges").Unprotect
    Sheet("AppTasks").Range("A2:K10000").ClearContents: Sheet("AppBadges").Range("A2:C10000").ClearContents
    tr = 2: br = 2: r = 6
    For Each p In shift("patients")
        ws.Cells(r, 1).Value2 = r - 5
        SetText ws.Cells(r, 2), Txt(p, "name")
        ws.Cells(r, 3).NumberFormat = "0"
        ws.Cells(r, 3).Formula = "=IF(T" & r & "="""","""",VALUE(LEFT($B$1,4))-VALUE(T" & r & "))"
        SetText ws.Cells(r, 4), Txt(p, "gender"): SetText ws.Cells(r, 5), Txt(p, "admitNum")
        SetText ws.Cells(r, 6), Txt(p, "admitDate"): SetText ws.Cells(r, 7), Txt(p, "diagnosisType")
        SetText ws.Cells(r, 8), Txt(p, "hasCompanion"): SetText ws.Cells(r, 9), Txt(p, "initialDiagnosis")
        SetText ws.Cells(r, 10), Txt(p, "treatmentPlan"): SetText ws.Cells(r, 11), Txt(p, "followUp")
        SetText ws.Cells(r, 12), Txt(p, "labs"): SetText ws.Cells(r, 13), DoctorLabel(doctors, Txt(p, "responsibleSupervisorId"))
        SetText ws.Cells(r, 14), DoctorLabel(doctors, Txt(p, "responsibleResidentId"))
        SetText ws.Cells(r, 17), Txt(p, "externalId"): SetText ws.Cells(r, 20), Txt(p, "birthDate")
        SetText ws.Cells(r, 18), Txt(p, "isPriority")
        ws.Cells(r, 16).NumberFormat = "0"
        ws.Cells(r, 16).Formula = "=IF(F" & r & "="""","""",DATE(LEFT($B$1,4),MID($B$1,6,2),RIGHT($B$1,2))-DATE(LEFT(F" & r & ",4),MID(F" & r & ",6,2),RIGHT(F" & r & ",2))+1)"
        Set tasks = ParseJson(Txt(p, "tasksJson", "[]"))
        For Each t In tasks
            SetText Sheet("AppTasks").Cells(tr, 1), Txt(p, "externalId")
            SetText Sheet("AppTasks").Cells(tr, 2), Txt(t, "id")
            SetText Sheet("AppTasks").Cells(tr, 3), Txt(t, "description")
            SetText Sheet("AppTasks").Cells(tr, 4), Txt(t, "priority", "NORMAL")
            SetText Sheet("AppTasks").Cells(tr, 5), Txt(t, "ownerDoctorId")
            SetText Sheet("AppTasks").Cells(tr, 6), TaskTime(Num(t, "dueAtEpochMillis"))
            SetText Sheet("AppTasks").Cells(tr, 7), Txt(t, "dueShiftDate")
            SetText Sheet("AppTasks").Cells(tr, 8), IIf(Flag(t, "done"), "Y", "N")
            SetText Sheet("AppTasks").Cells(tr, 9), Txt(t, "completedByDoctorId")
            SetText Sheet("AppTasks").Cells(tr, 10), Txt(t, "completedByName")
            SetText Sheet("AppTasks").Cells(tr, 11), Txt(t, "completedAtEpochMillis"): tr = tr + 1
        Next t
        badgeText = Txt(p, "warningDetails")
        If Len(badgeText) Then
            Need Left$(badgeText, 7) = "badges:", "Unsupported badge representation."
            badges = Split(Mid$(badgeText, 8), ";")
            For Each badge In badges
                pair = InStr(CStr(badge), ":"): Need pair > 1, "Invalid badge."
                badgeText = Mid$(badge, pair + 1): badgeText = Replace(Replace(badgeText, "-", "+"), "_", "/")
                Do While Len(badgeText) Mod 4: badgeText = badgeText & "=": Loop
                raw = Unbase64(badgeText)
                SetText Sheet("AppBadges").Cells(br, 1), Txt(p, "externalId")
                SetText Sheet("AppBadges").Cells(br, 2), TextUtf8(raw)
                SetText Sheet("AppBadges").Cells(br, 3), Left$(badge, pair - 1): br = br + 1
            Next badge
        End If
        r = r + 1
    Next p
    ShowDoctors doctors, Txt(meta, "doctors")
    StateSet "savedShift", Json(shift): StateSet "grid", GridKey()
    ws.Columns("Q:Q").Hidden = True: ws.Columns("S:S").Hidden = True
    ws.Range("A6:T1000").Locked = False: ws.Range("A6:A1000,C6:C1000,P6:Q1000,S6:S1000").Locked = True
    ws.Protect UserInterfaceOnly:=True, AllowFiltering:=True
    Sheet("AppTasks").Cells.Locked = False: Sheet("AppTasks").Range("I:K").Locked = True
    Sheet("AppTasks").Protect UserInterfaceOnly:=True, AllowFiltering:=True
    SaveLocalWorkbook
End Sub

Public Sub CheckGridShift(ByVal shift As Object, ByVal doctors As Object)
    Dim p As Variant, task As Variant, tasks As Object, field As Variant, taskCount As Long, badgeCount As Long
    Dim id As String, role As String, badge As Variant, entries As Variant, encoded As String, raw() As Byte, separator As Long
    Need shift("patients").Count <= 200, "App maximum is 200 patients."
    For Each p In shift("patients")
        ValidatePatient p
        For Each field In Split(ColumnsCsv, ",")
            If CStr(field) <> "tasksJson" And CStr(field) <> "warningDetails" Then Need Len(Txt(p, CStr(field))) <= 32767, "A clinical value exceeds Excel's cell limit. Nothing was replaced."
        Next field
        For Each field In Array("responsibleSupervisorId", "responsibleResidentId")
            id = Txt(p, CStr(field))
            If Len(id) Then
                Need doctors.Exists(id), "Published patient assignment is unavailable in the current registry."
                If CStr(field) = "responsibleSupervisorId" Then role = "SUPERVISOR" Else role = "RESIDENT"
                Need Txt(doctors(id), "clinicalRole") = role, "Patient assignment has the wrong clinical role."
            End If
        Next field
        Set tasks = ParseJson(Txt(p, "tasksJson", "[]"))
        For Each task In tasks
            Need Len(Txt(task, "description")) <= 32767, "Task exceeds Excel's cell limit; no truncation allowed."
            taskCount = taskCount + 1
        Next task
        If Len(Txt(p, "warningDetails")) Then
            entries = Split(Mid$(Txt(p, "warningDetails"), 8), ";")
            For Each badge In entries
                separator = InStr(CStr(badge), ":"): encoded = Mid$(badge, separator + 1)
                encoded = Replace(Replace(encoded, "-", "+"), "_", "/")
                Do While Len(encoded) Mod 4: encoded = encoded & "=": Loop
                raw = Unbase64(encoded): Need Len(TextUtf8(raw)) <= 32767, "Badge exceeds Excel's cell limit."
                badgeCount = badgeCount + 1
            Next badge
        End If
    Next p
    Need taskCount <= 9999 And badgeCount <= 9999, "Task/badge editor capacity exceeded; no rows were dropped."
End Sub

Public Function CaptureShift(ByVal doctors As Object, ByVal actor As Object, ByVal initial As Boolean) As Object
    Dim saved As Object, result As Object, meta As Object, old As Object, patients As New Collection, p As Object
    Dim ws As Worksheet, r As Long, last As Long, id As String, ids As Object, columns As Variant, key As Variant
    Dim roster As String, doctorId As String, dateText As String, birth As String, note As String
    Set ws = Sheet("Data"): dateText = IsoDate(ws.Range("B1").Value2)
    Need dateText = ShiftToday(), "Only the current Asia/Damascus 08:30 shift can be edited/published. Set the explicit shift date."
    If initial Then
        Set result = Dict(): Set meta = Dict(): Set old = Dict()
        For Each key In Array("schemaVersion", "shiftDate", "shiftId", "doctors", "sortSpec", "shiftRevision", "snapshotId", "baseSnapshotId", "deviceId")
            Put meta, CStr(key), ""
        Next key
        Put meta, "schemaVersion", "7": Put meta, "shiftDate", dateText: Put meta, "shiftId", NewId()
        Put meta, "sortSpec", "{""levels"":[]}": Put meta, "shiftRevision", "0": Put result, "meta", meta
    Else
        Need Len(StateText("savedShift")) > 0, "Retrieve or explicitly import the initial rows first."
        Set saved = ParseJson(StateText("savedShift")): Set result = Clone(saved): Set meta = result("meta"): Set old = PatientMap(saved)
        Need dateText = Txt(meta, "shiftDate"), "Changing B1 cannot move an existing shift. Retrieve the current shift instead."
    End If
    Set ids = Dict(): columns = Split(ColumnsCsv, ",")
    last = ws.Cells(ws.Rows.Count, 2).End(xlUp).Row
    Need last <= 1000, "Patient grid exceeds the supported row range."
    For r = 6 To last
        If Len(Trim$(CStr(ws.Cells(r, 2).Value2))) Then
            id = CStr(ws.Cells(r, 17).Value2)
            If initial Then Need Len(id) = 0, "Initial import requires unlinked rows."
            If Len(id) = 0 Then id = NewId() Else Need old.Exists(id), "Unknown patient UUID; never paste another project's identities."
            Need Not ids.Exists(id), "Duplicate patient UUID. Copy clinical cells only when adding a new patient."
            ids.Add id, True
            If old.Exists(id) Then
                Set p = Clone(old(id))
            Else
                Set p = Dict(): For Each key In columns: Put p, CStr(key), "": Next key
                Put p, "tasksJson", "[]": Put p, "revision", "0": Put p, "isPriority", "N"
            End If
            Put p, "externalId", id: Put p, "name", CStr(ws.Cells(r, 2).Value2)
            birth = CStr(ws.Cells(r, 20).Value2)
            If Len(birth) = 0 And initial Then
                Need IsNumeric(ws.Cells(r, 3).Value2), "An original age is missing; enter birth year in column T."
                birth = CStr(CLng(Left$(dateText, 4)) - CLng(ws.Cells(r, 3).Value2))
            End If
            Put p, "birthDate", birth: Put p, "gender", EnumCell(ws.Cells(r, 4), "gender")
            Put p, "admitNum", CStr(ws.Cells(r, 5).Value2): Put p, "admitDate", IsoDate(ws.Cells(r, 6).Value2)
            Put p, "diagnosisType", EnumCell(ws.Cells(r, 7), "diagnosis")
            Put p, "hasCompanion", EnumCell(ws.Cells(r, 8), "yes")
            Put p, "initialDiagnosis", CStr(ws.Cells(r, 9).Value2): Put p, "treatmentPlan", CStr(ws.Cells(r, 10).Value2)
            note = CStr(ws.Cells(r, 11).Value2)
            If Len(Trim$(CStr(ws.Cells(r, 15).Value2))) Then note = note & vbLf & "[Excel reviewing doctor: " & CStr(ws.Cells(r, 15).Value2) & "]"
            Put p, "followUp", note: Put p, "labs", CStr(ws.Cells(r, 12).Value2)
            Put p, "responsibleSupervisorId", ResolveDoctor(CStr(ws.Cells(r, 13).Value2), "SUPERVISOR", doctors)
            Put p, "responsibleResidentId", ResolveDoctor(CStr(ws.Cells(r, 14).Value2), "RESIDENT", doctors)
            If Not initial Then
                Put p, "isPriority", EnumCell(ws.Cells(r, 18), "yes")
                CaptureTasks p, old, doctors, actor
                CaptureBadges p
            End If
            ValidatePatient p
            Need Len(Trim$(Txt(p, "initialDiagnosis"))) > 0 And Len(Trim$(Txt(p, "treatmentPlan"))) > 0 And Len(Trim$(Txt(p, "followUp"))) > 0, "Diagnosis, treatment and follow-up are required."
            Need Txt(p, "admitDate") <= Format$(#1/1/1970# + (EpochMs() + 10800000#) / 86400000#, "yyyy-mm-dd"), "Future admission date."
            Need CLng(Left$(dateText, 4)) - CLng(birth) >= 0 And CLng(Left$(dateText, 4)) - CLng(birth) <= 120, "Birth year/age outside the supported range."
            If Not old.Exists(id) Then
                Stamp p, actor, 1
            ElseIf EditablePatient(p) <> EditablePatient(old(id)) Then
                Stamp p, actor, Num(old(id), "revision") + 1
            End If
            patients.Add p
        End If
    Next r
    Need patients.Count <= 200, "App maximum is 200 patients."
    Set ws = Sheet("AppDoctors")
    For r = 2 To ws.Cells(ws.Rows.Count, 1).End(xlUp).Row
        If UCase$(CStr(ws.Cells(r, 4).Value2)) = "Y" Then
            doctorId = CStr(ws.Cells(r, 1).Value2): Need doctors.Exists(doctorId), "Roster includes an inactive/unknown doctor."
            If Len(roster) Then roster = roster & ","
            roster = roster & doctorId
        End If
    Next r
    Need Len(roster) > 0, "Select at least one shift doctor: AppDoctors column D = Y."
    Put meta, "doctors", roster
    'Excel's physical row order is the reviewed stored order on every client.
    Dim reordered As Boolean, position As Long
    reordered = initial
    If Not initial Then
        reordered = patients.Count <> saved("patients").Count
        If Not reordered Then
            For position = 1 To patients.Count
                If Txt(patients(position), "externalId") <> Txt(saved("patients")(position), "externalId") Then reordered = True
            Next position
        End If
    End If
    If reordered Then Put meta, "sortSpec", "{""levels"":[]}"
    Put result, "patients", patients
    ValidateOrphanRows ids
    Set CaptureShift = result
End Function

Private Sub Stamp(ByVal p As Object, ByVal actor As Object, ByVal revision As Double)
    Put p, "lastEditedByDoctorId", Txt(actor, "uid"): Put p, "lastEditedBy", Txt(actor, "name")
    Put p, "updatedAt", Format$(EpochMs(), "0"): Put p, "revision", Format$(revision, "0")
End Sub

Public Function EditablePatient(ByVal p As Object) As String
    Dim copy As Object, key As Variant: Set copy = Clone(p)
    For Each key In Array("lastEditedByDoctorId", "lastEditedBy", "updatedAt", "revision")
        If copy.Exists(CStr(key)) Then copy.Remove CStr(key)
    Next key
    EditablePatient = Json(copy)
End Function

Private Function EnumCell(ByVal cell As Range, ByVal kind As String) As String
    Dim text As String, ws As Worksheet
    text = Trim$(CStr(cell.Value2)): Set ws = Sheet("Settings")
    Select Case kind
        Case "gender"
            If text = CStr(ws.Range("D50").Value2) Then text = "F"
            If text = CStr(ws.Range("D51").Value2) Then text = "M"
            Need text = "M" Or text = "F", "Gender must be M/F or the original Arabic choices."
        Case "diagnosis"
            If text = CStr(ws.Range("D52").Value2) Then text = "PSYCHIATRIC"
            If text = CStr(ws.Range("D53").Value2) Then text = "DUAL"
            If text = CStr(ws.Range("D54").Value2) Then text = "ADDICTION"
            Need text = "PSYCHIATRIC" Or text = "DUAL" Or text = "ADDICTION", "Choose a valid diagnosis category."
        Case "yes"
            If text = CStr(ws.Range("D42").Value2) Then text = "Y"
            If text = CStr(ws.Range("D43").Value2) Then text = "N"
            If Len(text) = 0 Then text = "N"
            Need text = "Y" Or text = "N", "Use Y/N for companion, priority and done."
    End Select
    EnumCell = text
End Function

Private Sub CaptureTasks(ByVal p As Object, ByVal oldPatients As Object, ByVal doctors As Object, ByVal actor As Object)
    Dim ws As Worksheet, r As Long, id As String, task As Object, previous As Object, tasks As New Collection
    Dim before As Object, oldTasks As Object, t As Variant, seen As Object, owner As String, field As Variant
    Set oldTasks = Dict(): Set seen = Dict()
    If oldPatients.Exists(Txt(p, "externalId")) Then
        Set before = ParseJson(Txt(oldPatients(Txt(p, "externalId")), "tasksJson", "[]"))
        For Each t In before: oldTasks.Add Txt(t, "id"), t: Next t
    End If
    Set ws = Sheet("AppTasks")
    For r = 2 To ws.Cells(ws.Rows.Count, 1).End(xlUp).Row
        If CStr(ws.Cells(r, 1).Value2) = Txt(p, "externalId") Then
            id = CStr(ws.Cells(r, 2).Value2): If Len(id) = 0 Then id = NewId()
            Need Not seen.Exists(id), "Duplicate task ID.": seen.Add id, True
            If oldTasks.Exists(id) Then Set previous = oldTasks(id): Set task = Clone(previous) Else Set previous = Dict(): Set task = Dict()
            Put task, "id", id: Put task, "description", Trim$(CStr(ws.Cells(r, 3).Value2))
            Put task, "priority", UCase$(CStr(ws.Cells(r, 4).Value2))
            If Len(Txt(task, "priority")) = 0 Then Put task, "priority", "NORMAL"
            owner = CStr(ws.Cells(r, 5).Value2)
            Put task, "done", EnumCell(ws.Cells(r, 8), "yes") = "Y"
            If Len(owner) Then
                Need doctors.Exists(owner) Or (Flag(previous, "done") And Flag(task, "done") And Txt(previous, "ownerDoctorId") = owner), "Pending task owner must be an active doctor UUID."
                Put task, "ownerDoctorId", owner
                If doctors.Exists(owner) Then Put task, "ownerName", Txt(doctors(owner), "name")
            Else
                Put task, "ownerDoctorId", Null: Put task, "ownerName", Null
            End If
            If Len(CStr(ws.Cells(r, 6).Value2)) Then
                If CStr(ws.Cells(r, 6).Value2) <> TaskTime(Num(previous, "dueAtEpochMillis")) Then Put task, "dueAtEpochMillis", TaskMillis(CStr(ws.Cells(r, 6).Value2))
            Else
                Put task, "dueAtEpochMillis", Null
            End If
            If Len(CStr(ws.Cells(r, 7).Value2)) Then Put task, "dueShiftDate", IsoDate(ws.Cells(r, 7).Value2) Else Put task, "dueShiftDate", Null
            If Flag(task, "done") Then
                If Not Flag(previous, "done") Then
                    Put task, "completedByDoctorId", Txt(actor, "uid"): Put task, "completedByName", Txt(actor, "name")
                    Put task, "completedAtEpochMillis", EpochMs()
                End If
            Else
                For Each field In Array("completedByDoctorId", "completedByName", "completedAtEpochMillis"): Put task, CStr(field), Null: Next field
            End If
            tasks.Add task
        End If
    Next r
    Put p, "tasksJson", Json(tasks)
End Sub

Private Sub CaptureBadges(ByVal p As Object)
    Dim ws As Worksheet, r As Long, text As String, level As String, result As String, flags As String, raw() As Byte
    Set ws = Sheet("AppBadges")
    For r = 2 To ws.Cells(ws.Rows.Count, 1).End(xlUp).Row
        If CStr(ws.Cells(r, 1).Value2) = Txt(p, "externalId") Then
            text = CStr(ws.Cells(r, 2).Value2): level = CStr(ws.Cells(r, 3).Value2)
            Need Len(Trim$(text)) > 0 And (level = "none" Or level = "low" Or level = "medium" Or level = "high"), "Badge text/level is invalid."
            raw = Utf8(text): text = Replace(Replace(Replace(Base64(raw), "=", ""), "+", "-"), "/", "_")
            If Len(result) Then result = result & ";"
            result = result & level & ":" & text
            If level <> "none" Then
                If Len(flags) Then flags = flags & "|"
                flags = flags & level
            End If
        End If
    Next r
    If Len(result) Then result = "badges:" & result
    Put p, "warningDetails", result: Put p, "warningFlags", flags
End Sub

Private Sub ValidateOrphanRows(ByVal ids As Object)
    Dim name As Variant, ws As Worksheet, r As Long
    For Each name In Array("AppTasks", "AppBadges")
        Set ws = Sheet(CStr(name))
        For r = 2 To ws.Cells(ws.Rows.Count, 1).End(xlUp).Row
            If Len(CStr(ws.Cells(r, 1).Value2)) Then Need ids.Exists(CStr(ws.Cells(r, 1).Value2)), "Tasks/badges reference a removed patient. Remove those rows explicitly with the patient."
        Next r
    Next name
End Sub
