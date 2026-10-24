Attribute VB_Name = "SRWorkflows"
Option Explicit

Private Function EmptyShift(ByVal dateText As String) As Object
    Dim meta As Object, patients As New Collection, key As Variant
    Set EmptyShift = Dict(): Set meta = Dict()
    For Each key In Array("schemaVersion", "shiftDate", "shiftId", "doctors", "sortSpec", "shiftRevision", "snapshotId", "baseSnapshotId", "deviceId")
        Put meta, CStr(key), ""
    Next key
    Put meta, "schemaVersion", "7": Put meta, "shiftDate", dateText: Put meta, "shiftId", NewId()
    Put meta, "sortSpec", "{""levels"":[]}": Put meta, "shiftRevision", "0"
    Put EmptyShift, "meta", meta: Put EmptyShift, "patients", patients
End Function

Public Function FetchBundle(ByVal state As Object) As Collection
    Dim raw() As Byte
    If Len(Txt(state, "csvFileId")) = 0 Then Set FetchBundle = New Collection: Exit Function
    raw = Download(Txt(state, "csvFileId")): Set FetchBundle = DecodeBundle(TextUtf8(raw))
End Function

Public Function FindShift(ByVal bundle As Collection, ByVal dateText As String) As Object
    Dim shift As Variant, count As Long
    For Each shift In bundle
        If Txt(shift("meta"), "shiftDate") = dateText Then Set FindShift = shift: count = count + 1
    Next shift
    Need count <= 1, "Ambiguous duplicate shift date in Telegram bundle."
End Function

Public Sub RetrieveData()
    Dim state As Object, doctors As Object, actor As Object, bundle As Collection, selected As Object, saved As Object, published As Object
    Dim indexId As String, dateText As String, answer As Variant, description As String, shift As Variant, initialGrid As String
    Need Connected(), "Open the protected project first."
    initialGrid = GridKey()
    If Len(StateText("savedShift")) Then
        Need GridKey() = StateText("grid"), "Unsaved Excel edits must be saved/reviewed before a download can replace them."
        Set saved = ParseJson(StateText("savedShift"))
        Need Len(StateText("publishedShift")) > 0, "There are saved unpublished rows. Review/publish them before retrieving."
        Set published = ParseJson(StateText("publishedShift"))
        Need ClinicalKey(saved) = ClinicalKey(published), "Saved local changes are unpublished; retrieve would replace them."
    End If
    Set state = ReadIndex(indexId): Set doctors = FetchDoctors(state): Set actor = Authorize(doctors): Set bundle = FetchBundle(state)
    For Each shift In bundle: description = description & Txt(shift("meta"), "shiftDate") & vbCrLf: Next shift
    answer = Application.InputBox("Available dates:" & vbCrLf & description & "Enter YYYY-MM-DD. Current shift " & ShiftToday() & " may start empty.", "Retrieve complete snapshot", ShiftToday(), Type:=2)
    If VarType(answer) = vbBoolean Then Exit Sub
    dateText = IsoDate(CStr(answer)): Set selected = FindShift(bundle, dateText)
    If selected Is Nothing Then
        Need dateText = ShiftToday(), "That archived shift is not available in the published bundle."
        Set selected = EmptyShift(dateText)
    End If
    Need Len(StateText("knownMessage")) = 0 Or Num(state, "csvMessageId") >= Val(StateText("knownMessage")), "Published snapshot is older than the last known snapshot. Replacement blocked."
    Need MsgBox("Replace the Excel clinical view with this COMPLETE published shift? A local workbook recovery copy will be saved first.", vbYesNo + vbQuestion + vbDefaultButton2) = vbYes, "Retrieve cancelled."
    Need GridKey() = initialGrid, "Local cells changed during retrieval. Nothing was replaced."
    CheckGridShift selected, doctors
    SnapshotBackup
    StateSet "publishedShift", Json(selected): StateSet "baselineState", Json(state)
    StateSet "knownMessage", Txt(state, "csvMessageId")
    WriteShift selected, doctors
    MsgBox "Retrieved and validated. Save edits locally, then review/publish explicitly.", vbInformation
End Sub

Public Sub ImportInitialRows()
    Dim state As Object, doctors As Object, actor As Object, bundle As Collection, remote As Object, candidate As Object
    Dim indexId As String
    Need Len(StateText("savedShift")) = 0, "Initial import is only for unlinked rows. Use ordinary Save after retrieving/importing."
    Set state = ReadIndex(indexId): Set doctors = FetchDoctors(state): Set actor = Authorize(doctors): Set bundle = FetchBundle(state)
    Set remote = FindShift(bundle, ShiftToday())
    If Not remote Is Nothing Then Need remote("patients").Count = 0, "Today's remote shift already contains patients. Initial import is blocked. Retrieve it; do not substitute original Excel rows."
    Need MsgBox("Import the ORIGINAL Excel rows into an empty current shift? Confirm B1 is the intended shift date, match all doctor names and select the full roster in AppDoctors (D=Y). Original ages estimate birth years; review column T. Auditor O becomes a labelled follow-up note. This saves locally only.", vbYesNo + vbQuestion + vbDefaultButton2) = vbYes, "Initial import cancelled."
    SnapshotBackup: Set candidate = CaptureShift(doctors, actor, True)
    If Not remote Is Nothing Then Put candidate, "meta", Clone(remote("meta")): Put candidate("meta"), "doctors", RosterSelection(doctors)
    If remote Is Nothing Then Set remote = EmptyShift(ShiftToday()): Put remote("meta"), "shiftId", Txt(candidate("meta"), "shiftId")
    StateSet "publishedShift", Json(remote): StateSet "baselineState", Json(state)
    WriteShift candidate, doctors
    MsgBox "Imported locally. Review the estimated birth years, roster and clinical fields before publishing.", vbInformation
End Sub

Private Function RosterSelection(ByVal doctors As Object) As String
    Dim ws As Worksheet, r As Long, id As String
    Set ws = Sheet("AppDoctors")
    For r = 2 To ws.Cells(ws.Rows.Count, 1).End(xlUp).Row
        If CStr(ws.Cells(r, 4).Value2) = "Y" Then
            id = CStr(ws.Cells(r, 1).Value2): Need doctors.Exists(id), "Inactive roster identity."
            If Len(RosterSelection) Then RosterSelection = RosterSelection & ","
            RosterSelection = RosterSelection & id
        End If
    Next r
    Need Len(RosterSelection) > 0, "Select the full shift roster in AppDoctors."
End Function

Public Function SaveDraft() As Object
    Dim state As Object, doctors As Object, actor As Object, indexId As String, candidate As Object
    Set state = ReadIndex(indexId): Set doctors = FetchDoctors(state): Set actor = Authorize(doctors)
    Set candidate = CaptureShift(doctors, actor, False)
    If Len(StateText("savedShift")) Then
        Dim previous As Object: Set previous = ParseJson(StateText("savedShift"))
        If candidate("patients").Count < previous("patients").Count Then
            Need MsgBox("Patients were removed from the Excel grid. Save these deletions locally? Publication still requires a separate complete-snapshot review.", vbYesNo + vbQuestion + vbDefaultButton2) = vbYes, "Local deletion cancelled."
            SnapshotBackup
        End If
    End If
    WriteShift candidate, doctors: Set SaveDraft = candidate
End Function

Public Sub SaveOnly()
    Dim saved As Object: Set saved = SaveDraft()
    MsgBox "Saved locally with clinician/revision stamps. No data was published.", vbInformation
End Sub

Private Sub ReviewSheet(ByVal local As Object, ByVal remote As Object, ByVal doctors As Object)
    Dim ws As Worksheet, p As Variant, r As Long, old As Object, id As String, state As Object
    Set ws = Sheet("AppReview"): ws.Unprotect: ws.Cells.ClearContents: ws.Cells.NumberFormat = "@"
    ws.Cells(1, 1).Value2 = "COMPLETE LOCAL REPLACEMENT - REVIEW BEFORE PUBLICATION"
    ws.Cells(2, 1).Value2 = "Shift " & Txt(local("meta"), "shiftDate") & "; patients " & local("patients").Count
    ws.Cells(3, 1).Value2 = "Roster: " & Txt(local("meta"), "doctors")
    ws.Cells(4, 1).Value2 = "Other devices must retrieve. Last successful pinned write wins; no atomic merge."
    If remote Is Nothing Then Set old = Dict() Else Set old = PatientMap(remote)
    r = 6
    For Each p In local("patients")
        id = Txt(p, "externalId")
        ws.Cells(r, 1).Value2 = IIf(old.Exists(id), "KEEP/EDIT", "ADD")
        ws.Cells(r, 2).Value2 = PatientSummary(p, doctors): r = r + 1
        If old.Exists(id) Then old.Remove id
    Next p
    For Each p In old.Items
        ws.Cells(r, 1).Value2 = "DELETE": ws.Cells(r, 2).Value2 = PatientSummary(p, doctors): r = r + 1
    Next p
    ws.Columns("A").ColumnWidth = 18: ws.Columns("B").ColumnWidth = 100
    ws.Range("A1:B" & r).WrapText = True: ws.Range("A1:B" & r).VerticalAlignment = xlTop
    ws.Rows("1:" & r).AutoFit: ws.Protect UserInterfaceOnly:=True
    ws.Activate: ws.Range("A1").Select
    'Modal Excel preview makes the full reviewed replacement accessible, without network writes.
    ws.PageSetup.PrintArea = "$A$1:$B$" & r
    ws.PageSetup.Orientation = xlPortrait: ws.PageSetup.Zoom = False: ws.PageSetup.FitToPagesWide = 1: ws.PageSetup.FitToPagesTall = False
    ws.PrintPreview
End Sub

Public Function PublishShift(ByVal candidate As Object, Optional ByVal notify As Boolean = True) As Object
    Dim state As Object, latestState As Object, doctors As Object, actor As Object, bundle As Collection, remote As Object
    Dim indexId As String, latestId As String, grid As String, reference As String, actorId As String, shifts As New Collection
    Dim other As Variant, p As Variant, map As Object, metadata As Object, receipt As String, uploaded As Object, at As Double
    Dim raw() As Byte, journal As Collection, entry As Object, key As Variant, snapshotId As String, published As Object
    LockReceipts
    Need Txt(candidate("meta"), "shiftDate") = ShiftToday(), "Only the current shift can be published."
    Need Json(candidate) = StateText("savedShift"), "Save this exact clinical snapshot locally before publication."
    grid = GridKey(): Set state = ReadIndex(indexId): Set doctors = FetchDoctors(state): Set actor = Authorize(doctors)
    CheckGridShift candidate, doctors
    actorId = Txt(actor, "uid"): Set bundle = FetchBundle(state): Set remote = FindShift(bundle, ShiftToday())
    If Not remote Is Nothing Then
        Need Txt(remote("meta"), "shiftId") = Txt(candidate("meta"), "shiftId"), "Today's published shift has another identity. Retrieve/reconcile it; this workbook will not force replacement."
        If ClinicalKey(remote) = ClinicalKey(candidate) Then
            StateSet "publishedShift", Json(remote): StateSet "baselineState", Json(state): SaveLocalWorkbook
            Set PublishShift = state: Exit Function
        End If
    End If
    'An outdated remote baseline requires a fresh retrieve/reconciliation rather than silent overwrite.
    If Len(StateText("baselineState")) Then
        Dim baseline As Object: Set baseline = ParseJson(StateText("baselineState"))
        Need Txt(baseline, "csvFileId") = Txt(state, "csvFileId") And Txt(baseline, "csvMessageId") = Txt(state, "csvMessageId"), "A different snapshot was published since this workbook's baseline. Preserve your workbook and retrieve in a separate copy to reconcile explicitly."
    End If
    ReviewSheet candidate, remote, doctors
    Need GridKey() = grid, "Excel cells changed during review. Save and review again."
    Need MsgBox("Publish the COMPLETE reviewed local shift, including deletions, roster, order, tasks and badges? This changes the shared snapshot for every device.", vbYesNo + vbQuestion + vbDefaultButton2) = vbYes, "Publication cancelled."
    reference = Pointers(state): Set latestState = ReadIndex(latestId)
    Need latestId = indexId And Pointers(latestState) = reference And GridKey() = grid, "The project/data changed after review. Nothing was published."
    Set doctors = FetchDoctors(latestState): Set actor = Authorize(doctors): Need Txt(actor, "uid") = actorId, "Actor changed. Review again."
    Set published = Clone(candidate): Set metadata = published("meta")
    snapshotId = NewId(): Put metadata, "snapshotId", snapshotId
    If Not remote Is Nothing Then
        Put metadata, "baseSnapshotId", Txt(remote("meta"), "snapshotId")
        Put metadata, "shiftRevision", Format$(Num(remote("meta"), "shiftRevision") + 1, "0")
    Else
        Put metadata, "baseSnapshotId", "": Put metadata, "shiftRevision", "1"
    End If
    If Len(StateText("device")) = 0 Then StateSet "device", "excel-" & NewId()
    Put metadata, "deviceId", StateText("device")
    shifts.Add published: Set map = PatientMap(published)
    For Each other In bundle
        If Txt(other("meta"), "shiftDate") <> ShiftToday() Then
            Need Txt(other("meta"), "shiftDate") < ShiftToday(), "Future shift in remote bundle; check device dates."
            For Each p In other("patients"): Need Not map.Exists(Txt(p, "externalId")), "Patient ID belongs to another shift.": Next p
            If shifts.Count < 3 Then shifts.Add other
        End If
    Next other
    raw = Utf8(EncodeBundle(shifts))
    receipt = BeginReceipt("publication", Txt(metadata, "shiftId"), Cfg("chatId"), Cfg("csvTopicId"), ClinicalKey(published), snapshotId)
    SaveLocalWorkbook
    Set uploaded = Upload(raw, Cfg("chatId"), Cfg("csvTopicId"), False)
    Set latestState = ReadIndex(latestId)
    Need latestId = indexId And Pointers(latestState) = reference, "Upload reached Telegram but a concurrent index change blocked publication. Inspect the UNKNOWN receipt."
    Set doctors = FetchDoctors(latestState): Set actor = Authorize(doctors)
    Need Txt(actor, "uid") = actorId And GridKey() = grid And ShiftToday() = Txt(metadata, "shiftDate"), "Actor, local data or shift changed during upload. Index was not changed."
    at = Num(uploaded, "date") * 1000#
    If Num(latestState, "csvUpdatedAt") = 0 Or at - Num(latestState, "csvUpdatedAt") >= 36000000# Then
        For Each key In Array("FileId", "MessageId", "Date", "UpdatedAt")
            If latestState.Exists("csv" & CStr(key)) Then Put latestState, "previousCsv" & CStr(key), latestState("csv" & CStr(key))
        Next key
    End If
    Put latestState, "csvFileId", Txt(uploaded("document"), "file_id"): Put latestState, "csvMessageId", Num(uploaded, "message_id")
    Put latestState, "csvDate", ShiftToday(): Put latestState, "csvUpdatedAt", at
    If latestState.Exists("publicationJournal") Then Set journal = latestState("publicationJournal") Else Set journal = New Collection
    Set entry = Dict(): Put entry, "snapshotId", snapshotId
    If Len(Txt(metadata, "baseSnapshotId")) Then Put entry, "parentSnapshotId", Txt(metadata, "baseSnapshotId") Else Put entry, "parentSnapshotId", Null
    Put entry, "fileId", Txt(uploaded("document"), "file_id"): Put entry, "messageId", Num(uploaded, "message_id")
    Put entry, "at", at: Put entry, "deviceId", StateText("device"): Put entry, "forced", False
    If journal.Count = 0 Then journal.Add entry Else journal.Add entry, Before:=1
    Do While journal.Count > 12: journal.Remove journal.Count: Loop
    Put latestState, "publicationJournal", journal
    WriteIndex indexId, latestState
    ConfirmReceipt receipt, Txt(uploaded, "message_id")
    StateSet "publishedShift", Json(published): StateSet "baselineState", Json(latestState)
    StateSet "knownMessage", Txt(uploaded, "message_id")
    WriteShift published, doctors
    Set PublishShift = latestState
    If notify Then MsgBox "Publication confirmed by reading back the pinned index. Other devices can retrieve it.", vbInformation
End Function

Public Sub SaveAndPublish()
    Dim candidate As Object, state As Object
    Set candidate = SaveDraft(): Set state = PublishShift(candidate)
End Sub

Public Sub SendReport(Optional ByVal supervisor As String = "", Optional ByVal exportOnly As Boolean = False)
    Dim candidate As Object, state As Object, doctors As Object, actor As Object, indexId As String
    Dim grid As String, clinical As String, registry As String, path As String, digest As String, raw() As Byte
    Dim destination As String, topic As String, label As String, published As Object, uploaded As Object, receipt As String
    Set candidate = SaveDraft(): clinical = ClinicalKey(candidate): grid = GridKey()
    Set state = ReadIndex(indexId): Set doctors = FetchDoctors(state): Set actor = Authorize(doctors)
    registry = RegistryPointer(state)
    Destination doctors, supervisor, destination, topic, label
    path = RenderPdf(candidate, doctors, supervisor): raw = ReadBytes(path): digest = Sha256(raw)
    If exportOnly Then
        Dim selected As Variant
        selected = Application.GetSaveAsFilename("ShiftReport_" & Txt(candidate("meta"), "shiftDate") & ".pdf", "PDF (*.pdf),*.pdf")
        If VarType(selected) = vbBoolean Then Exit Sub
        Need Len(Dir$(CStr(selected))) = 0, "Choose a new PDF filename; existing files are never overwritten."
        WriteBytes CStr(selected), raw: Exit Sub
    End If
    ThisWorkbook.FollowHyperlink path 'Failure stops here, before publication or sending.
    Need MsgBox("Open and review this exact PDF in your viewer. Confirm only after inspecting all pages." & vbCrLf & "Destination: " & label & vbCrLf & "Continue to publication review and final Send confirmation?", vbYesNo + vbQuestion + vbDefaultButton2) = vbYes, "Report cancelled before publication."
    Need GridKey() = grid, "Report cells changed after preview. Review again."
    Set state = PublishShift(candidate, False)
    Set doctors = FetchDoctors(state): Set actor = Authorize(doctors)
    Need RegistryPointer(state) = registry, "Doctor registry/destination changed; preview the report again."
    Set published = ParseJson(StateText("savedShift")): Need ClinicalKey(published) = clinical, "Published content differs from the preview."
    Destination doctors, supervisor, destination, topic, label
    Need MsgBox("FINAL SEND: send the exact reviewed PDF to " & label & "? Shared data publication is already confirmed; cancellation does not undo it.", vbYesNo + vbQuestion + vbDefaultButton2) = vbYes, "Delivery cancelled; publication remains confirmed."
    Dim current As Object, currentId As String
    Set current = ReadIndex(currentId): Need Pointers(current) = Pointers(state) And GridKey() = grid, "Data/destination changed after confirmation. Preview again."
    Set doctors = FetchDoctors(current): Set actor = Authorize(doctors)
    Need ShiftToday() = Txt(published("meta"), "shiftDate"), "Shift ended before sending."
    raw = ReadBytes(path): Need Sha256(raw) = digest, "The exact reviewed PDF is missing or changed."
    LockReceipts
    receipt = BeginReceipt("report", Txt(published("meta"), "shiftId"), destination, topic, Digest(clinical & "|" & supervisor & "|a5-v1"))
    Set uploaded = Upload(raw, destination, topic, True)
    ConfirmReceipt receipt, Txt(uploaded, "message_id")
    MsgBox "Telegram confirmed PDF delivery. Publication and delivery are recorded separately.", vbInformation
End Sub

Private Sub Destination(ByVal doctors As Object, ByVal supervisor As String, ByRef chat As String, ByRef topic As String, ByRef label As String)
    If Len(supervisor) = 0 Then
        chat = Cfg("chatId"): topic = Cfg("reportsTopicId"): label = "project Reports topic"
    Else
        Need doctors.Exists(supervisor), "Supervisor is no longer active."
        Need Txt(doctors(supervisor), "clinicalRole") = "SUPERVISOR", "Choose a supervisor."
        chat = Txt(doctors(supervisor), "supervisorGroupChatId"): topic = ""
        Need Len(chat) > 0 And Val(chat) < 0, "Supervisor has no published report destination."
        label = Txt(doctors(supervisor), "name") & " (published supervisor group)"
    End If
End Sub

Public Sub ChooseSupervisorReport()
    Dim id As Variant
    id = Application.InputBox("Copy a supervisor UUID from AppDoctors column A. There is no five-doctor limit.", "Supervisor PDF", Type:=2)
    If VarType(id) = vbBoolean Then Exit Sub
    SendReport CStr(id)
End Sub

Public Sub SortGrid()
    Dim ws As Worksheet, last As Long, r As Long
    Set ws = Sheet("Data"): ws.Unprotect: last = ws.Cells(ws.Rows.Count, 2).End(xlUp).Row
    Need last >= 6 And last <= 1000, "No patient rows or unsupported grid size."
    'Sort every app/identity column together; R is patient priority, never a temporary key.
    ws.Range("A6:T" & last).Sort Key1:=ws.Range("D6"), Order1:=xlAscending, Key2:=ws.Range("G6"), Order2:=xlAscending, Key3:=ws.Range("F6"), Order3:=xlAscending, Header:=xlNo
    For r = 6 To last: ws.Cells(r, 1).Value2 = r - 5: Next r
    ws.Protect UserInterfaceOnly:=True, AllowFiltering:=True
    MsgBox "Sorted locally with patient IDs. Save/review to publish this stored order.", vbInformation
End Sub
