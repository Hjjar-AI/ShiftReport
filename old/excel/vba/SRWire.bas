Attribute VB_Name = "SRWire"
Option Explicit
Public Const ColumnsCsv As String = "externalId,admitNum,admitDate,gender,name,birthDate,hasCompanion,diagnosisType,initialDiagnosis,treatmentPlan,followUp,labs,responsibleResidentId,responsibleSupervisorId,warningFlags,warningDetails,isPriority,lastEditedByDoctorId,lastEditedBy,updatedAt,revision,tasksJson"
Public Const BundleHeader As String = "# rushdShiftBundle=1"
Public Const BundleBoundary As String = "#==RUSHD_SHIFT_BOUNDARY_7F3A9C=="

Public Function CsvRead(ByVal text As String) As Collection
    Dim rows As New Collection, row As New Collection, field As String, quoted As Boolean
    Dim closed As Boolean, ch As String, i As Long
    If Left$(text, 1) = ChrW$(&HFEFF) Then text = Mid$(text, 2)
    text = Replace(text, vbCrLf, vbLf)
    For i = 1 To Len(text)
        ch = Mid$(text, i, 1)
        If quoted Then
            If ch = """" Then
                If Mid$(text, i + 1, 1) = """" Then field = field & """": i = i + 1 Else quoted = False: closed = True
            Else
                field = field & ch
            End If
        Else
            Select Case ch
                Case """": Need Len(field) = 0 And Not closed, "Invalid CSV quoting.": quoted = True
                Case ",": row.Add field: field = "": closed = False
                Case vbLf
                    row.Add field: rows.Add row: Set row = New Collection: field = "": closed = False
                Case Else
                    Need Not closed, "Unexpected data after CSV quote."
                    field = field & ch
            End Select
        End If
    Next i
    Need Not quoted, "Unterminated CSV field."
    If Len(field) > 0 Or row.Count > 0 Or closed Then row.Add field: rows.Add row
    Set CsvRead = rows
End Function

Public Function CsvField(ByVal value As String) As String
    CsvField = """" & Replace(value, """", """""") & """"
End Function

Public Function DecodeDoctors(ByVal text As String) As Object
    Dim rows As Variant, fields As Variant, header As Variant, i As Long, j As Long, d As Object, result As Object, team As String
    If Left$(text, 1) = ChrW$(&HFEFF) Then text = Mid$(text, 2)
    Need InStrRev(text, "@") > 0, "Invalid doctor registry trailer."
    text = Left$(text, InStrRev(text, "@") - 1): rows = Split(text, "|"): header = Split(rows(0), ",")
    Need rows(0) = "#,uid,name,gender,id,title,options,clinicalRole,supervisorGroupChatId,teamSupervisorId" Or rows(0) = "#,uid,name,gender,id,title,options,clinicalRole,supervisorGroupChatId", "Unsupported doctor registry."
    Set result = Dict()
    For i = 1 To UBound(rows)
        fields = Split(rows(i), ","): Need UBound(fields) = UBound(header), "Incomplete doctor registry row."
        Set d = Dict()
        For j = 0 To UBound(header): Put d, CStr(header(j)), CStr(fields(j)): Next j
        Need Len(Txt(d, "uid")) > 0 And Len(Trim$(Txt(d, "name"))) > 0, "Missing doctor identity."
        Need Not result.Exists(Txt(d, "uid")), "Duplicate doctor identity."
        Need Txt(d, "clinicalRole") = "RESIDENT" Or Txt(d, "clinicalRole") = "SUPERVISOR", "Invalid doctor role."
        Need Matches(Txt(d, "#"), "^[0-9]*$") And Num(d, "#") <= 2147483647#, "Invalid registry authority rank."
        If Len(Txt(d, "options")) Then
            Need Matches(Txt(d, "options"), "^[aA][1-9][0-9]*$"), "Invalid registry authority options."
            Need Num(d, "#") > 0 And LCase$(Txt(d, "options")) = "a" & Txt(d, "#"), "Inconsistent registry authority."
        End If
        If Len(Txt(d, "id")) Then Need Matches(Txt(d, "id"), "^[1-9][0-9]*$") And Num(d, "id") > 0, "Invalid linked doctor account."
        If Len(Txt(d, "supervisorGroupChatId")) Then Need Num(d, "supervisorGroupChatId") < 0 And Txt(d, "clinicalRole") = "SUPERVISOR", "Invalid supervisor destination."
        result.Add Txt(d, "uid"), d
    Next i
    Need result.Count > 0, "Doctor registry is empty."
    For Each fields In result.Keys
        Set d = result(fields): team = Txt(d, "teamSupervisorId")
        If Len(team) Then
            Need Txt(d, "clinicalRole") = "RESIDENT" And result.Exists(team), "Invalid doctor team."
            Need Txt(result(team), "clinicalRole") = "SUPERVISOR", "Invalid team supervisor."
        End If
    Next fields
    Set DecodeDoctors = result
End Function

Public Function DecodeBundle(ByVal text As String) As Collection
    Dim result As New Collection, blocks As Variant, block As Variant, rows As Collection, meta As Object, shift As Object
    Dim patients As Collection, row As Collection, p As Object, columns As Variant, tokens As Variant, token As Variant
    Dim key As String, separator As Long, i As Long, j As Long, ids As Object, shiftIds As Object
    text = Replace(text, vbCrLf, vbLf)
    If Left$(text, 1) = ChrW$(&HFEFF) Then text = Mid$(text, 2)
    Need Left$(text, Len(BundleHeader) + 1) = BundleHeader & vbLf, "The current CSV bundle is required; XLSX pins are unsupported."
    blocks = Split(Mid$(text, Len(BundleHeader) + 2), vbLf & BundleBoundary & vbLf)
    Need UBound(blocks) <= 2, "Too many shifts in bundle.": columns = Split(ColumnsCsv, ","): Set shiftIds = Dict()
    For Each block In blocks
        Set rows = CsvRead(CStr(block)): Need rows.Count >= 2, "Missing CSV metadata/header."
        Set row = rows(1): Need row.Count = 1, "CSV metadata must be a single quoted field."
        Need Left$(row(1), 2) = "# ", "Missing CSV metadata prefix.": tokens = Split(Mid$(row(1), 3), " "): Set meta = Dict()
        For Each token In tokens
            separator = InStr(CStr(token), "="): Need separator > 1, "Invalid CSV metadata token."
            key = Left$(token, separator - 1): Need Not meta.Exists(key), "Duplicate shift metadata."
            meta.Add key, Mid$(token, separator + 1)
        Next token
        Need Txt(meta, "schemaVersion") = "7", "Patient schema 7 is required."
        For Each token In Array("shiftDate", "shiftId", "doctors", "sortSpec", "shiftRevision", "snapshotId", "baseSnapshotId", "deviceId")
            Need meta.Exists(CStr(token)), "Incomplete app shift metadata."
        Next token
        Need IsoDate(Txt(meta, "shiftDate")) = Txt(meta, "shiftDate"), "Invalid shift date."
        Need Len(Txt(meta, "shiftId")) > 0 And Not shiftIds.Exists(Txt(meta, "shiftId")), "Duplicate/missing shift identity."
        shiftIds.Add Txt(meta, "shiftId"), True
        Set row = rows(2): Need row.Count = 22, "Expected all 22 app columns."
        For j = 0 To 21: Need row(j + 1) = columns(j), "App column names/order do not match.": Next j
        Set patients = New Collection: Set ids = Dict()
        For i = 3 To rows.Count
            Set row = rows(i)
            If row.Count <> 1 Or Len(row(1)) <> 0 Then
                Need row.Count = 22, "Incomplete patient row.": Set p = Dict()
                For j = 0 To 21: Put p, CStr(columns(j)), CStr(row(j + 1)): Next j
                ValidatePatient p
                Need Not ids.Exists(Txt(p, "externalId")), "Duplicate patient identity."
                ids.Add Txt(p, "externalId"), True: patients.Add p
            End If
        Next i
        Need patients.Count <= 200, "More than 200 patients in the shift."
        Set shift = Dict(): Put shift, "meta", meta: Put shift, "patients", patients: result.Add shift
    Next block
    Set DecodeBundle = result
End Function

Public Function EncodeBundle(ByVal shifts As Collection) As String
    Dim shift As Object, meta As Object, patient As Variant, columns As Variant, key As Variant, line As String, text As String, index As Long
    Need shifts.Count >= 1 And shifts.Count <= 3, "Bundle supports one to three shifts."
    columns = Split(ColumnsCsv, ","): text = BundleHeader & vbLf
    For Each shift In shifts
        If index > 0 Then text = text & BundleBoundary & vbLf
        Set meta = shift("meta"): line = "# "
        For Each key In meta.Keys
            Need InStr(Txt(meta, CStr(key)), " ") = 0 And InStr(Txt(meta, CStr(key)), vbLf) = 0 And InStr(Txt(meta, CStr(key)), vbTab) = 0, "Whitespace in shift metadata."
            If Len(line) > 2 Then line = line & " "
            line = line & CStr(key) & "=" & Txt(meta, CStr(key))
        Next key
        text = text & CsvField(line) & vbLf & ColumnsCsv & vbLf
        For Each patient In shift("patients")
            ValidatePatient patient: line = ""
            For Each key In columns
                Need InStr(Txt(patient, CStr(key)), vbLf & BundleBoundary) = 0, "Patient text conflicts with the bundle separator."
                If Len(line) > 0 Then line = line & ","
                line = line & CsvField(Txt(patient, CStr(key)))
            Next key
            text = text & line & vbLf
        Next patient
        index = index + 1
    Next shift
    EncodeBundle = text
End Function

Public Sub ValidatePatient(ByVal p As Object)
    Dim tasks As Object, task As Variant, ids As Object, due As String, badges As Variant, badge As Variant, encoded As String, separator As Long, level As String, raw() As Byte
    Need Len(Trim$(Txt(p, "externalId"))) > 0 And Len(Trim$(Txt(p, "name"))) > 0 And Len(Trim$(Txt(p, "admitNum"))) > 0, "Patient ID, name and admission number are required."
    Need Txt(p, "gender") = "M" Or Txt(p, "gender") = "F", "Gender must be M/F."
    Need Txt(p, "hasCompanion") = "Y" Or Txt(p, "hasCompanion") = "N", "Companion must be Y/N."
    Need Txt(p, "isPriority") = "Y" Or Txt(p, "isPriority") = "N", "Priority must be Y/N."
    Need Txt(p, "diagnosisType") = "PSYCHIATRIC" Or Txt(p, "diagnosisType") = "ADDICTION" Or Txt(p, "diagnosisType") = "DUAL", "Invalid diagnosis category."
    Need Matches(Txt(p, "birthDate"), "^[0-9]{4}$"), "Four-digit birth year required."
    Need Num(p, "birthDate") >= 1900 And Num(p, "birthDate") <= 2100, "Birth year out of range."
    due = IsoDate(Txt(p, "admitDate")): Need Matches(Txt(p, "revision", "0"), "^[0-9]+$"), "Invalid patient revision."
    Set tasks = ParseJson(Txt(p, "tasksJson", "[]")): Need TypeName(tasks) = "Collection", "Tasks must be an array."
    Set ids = Dict()
    For Each task In tasks
        Need Len(Txt(task, "id")) > 0 And Len(Trim$(Txt(task, "description"))) > 0, "Task ID/description required."
        If task.Exists("done") Then Need VarType(task("done")) = vbBoolean, "Task done must be boolean."
        Need Not ids.Exists(Txt(task, "id")), "Duplicate task identity.": ids.Add Txt(task, "id"), True
        due = Txt(task, "priority", "NORMAL"): Need due = "LOW" Or due = "NORMAL" Or due = "HIGH", "Invalid task priority."
        Need Num(task, "dueAtEpochMillis") >= 0 And Num(task, "dueAtEpochMillis") = Fix(Num(task, "dueAtEpochMillis")), "Invalid task deadline."
        If Len(Txt(task, "dueShiftDate")) Then
            due = IsoDate(Txt(task, "dueShiftDate")): Need Num(task, "dueAtEpochMillis") > 0, "Task shift date requires a deadline."
        End If
        If Flag(task, "done") Then
            Need Len(Txt(task, "completedByDoctorId")) > 0 And Len(Txt(task, "completedByName")) > 0 And Num(task, "completedAtEpochMillis") > 0, "Incomplete task completion evidence."
        Else
            Need Len(Txt(task, "completedByDoctorId")) = 0 And Len(Txt(task, "completedByName")) = 0 And Num(task, "completedAtEpochMillis") = 0, "Pending task has completion evidence."
        End If
    Next task
    due = Txt(p, "warningDetails")
    If Len(due) Then
        Need Left$(due, 7) = "badges:", "Unsupported badge encoding."
        badges = Split(Mid$(due, 8), ";")
        For Each badge In badges
            separator = InStr(CStr(badge), ":"): Need separator > 1, "Invalid badge entry."
            level = Left$(badge, separator - 1)
            Need level = "none" Or level = "low" Or level = "medium" Or level = "high", "Invalid badge level."
            encoded = Mid$(badge, separator + 1): encoded = Replace(Replace(encoded, "-", "+"), "_", "/")
            Do While Len(encoded) Mod 4: encoded = encoded & "=": Loop
            raw = Unbase64(encoded): Need Len(Trim$(TextUtf8(raw))) > 0, "Empty badge text."
        Next badge
    End If
End Sub

Public Function PatientMap(ByVal shift As Object) As Object
    Dim p As Variant: Set PatientMap = Dict()
    For Each p In shift("patients"): PatientMap.Add Txt(p, "externalId"), p: Next p
End Function

Public Function ClinicalKey(ByVal shift As Object) As String
    Dim copy As Object, p As Variant, key As Variant, meta As Object
    Set copy = Clone(shift): Set meta = copy("meta")
    For Each key In Array("snapshotId", "baseSnapshotId", "deviceId", "shiftRevision")
        If meta.Exists(CStr(key)) Then meta.Remove CStr(key)
    Next key
    For Each p In copy("patients")
        For Each key In Array("lastEditedByDoctorId", "lastEditedBy", "updatedAt", "revision")
            If p.Exists(CStr(key)) Then p.Remove CStr(key)
        Next key
    Next p
    ClinicalKey = Digest(Json(copy))
End Function
