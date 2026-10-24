Attribute VB_Name = "SRTelegram"
Option Explicit
Private Declare PtrSafe Function CredUIPromptForCredentialsW Lib "credui" (ByRef info As Any, ByVal target As LongPtr, ByVal reserved As LongPtr, ByVal authError As Long, ByVal user As LongPtr, ByVal userMax As Long, ByVal password As LongPtr, ByVal passwordMax As Long, ByRef save As Long, ByVal flags As Long) As Long
Private Declare PtrSafe Sub CopyMemory Lib "kernel32" Alias "RtlMoveMemory" (ByRef target As Any, ByRef source As Any, ByVal count As LongPtr)
Private project As Object, dataKey() As Byte
Private actorId As String, actorTelegram As String, expires As Double
Private pendingDoctor As String, pendingTelegram As String, pendingHash As String, pendingSalt As String
Private challengeAt As Double, challengeAttempts As Long

Public Sub DisconnectProject()
    Set project = Nothing: Erase dataKey
    actorId = "": actorTelegram = "": expires = 0
    pendingDoctor = "": pendingHash = "": pendingSalt = "": challengeAt = 0
End Sub

Public Function Connected() As Boolean
    Connected = Not project Is Nothing
End Function

Public Function Cfg(ByVal key As String) As String
    Need Connected(), "Open a protected .srjoin.json project file first."
    Cfg = Txt(project, key)
End Function

Public Function ProjectFingerprint(ByVal config As Object) As String
    Dim key As Variant, identity As String
    For Each key In Array("chatId", "csvTopicId", "reportsTopicId", "doctorsTopicId", "telegramDataKey")
        identity = identity & CStr(key) & ":" & Txt(config, CStr(key)) & vbLf
    Next key
    identity = identity & Split(Txt(config, "botToken"), ":")(0)
    ProjectFingerprint = Digest(identity)
End Function

Public Function ProjectId() As String
    Need Connected(), "Open the protected project file first."
    ProjectId = ProjectFingerprint(project)
End Function

Private Function SecretPrompt() As String
    Dim info() As Byte, username As String, password As String, caption As String, message As String
    Dim handle As LongPtr, ptr As LongPtr, size As Long, status As Long, save As Long
    caption = "ShiftReport protected project"
    message = "Enter the join-file password. The username is ignored. Nothing is saved."
    username = "Join file" & String$(247, vbNullChar): password = String$(512, vbNullChar): handle = Application.Hwnd
    #If Win64 Then
        size = 40: ReDim info(0 To 39): CopyMemory info(8), handle, 8
        ptr = StrPtr(message): CopyMemory info(16), ptr, 8
        ptr = StrPtr(caption): CopyMemory info(24), ptr, 8
    #Else
        size = 20: ReDim info(0 To 19): CopyMemory info(4), handle, 4
        ptr = StrPtr(message): CopyMemory info(8), ptr, 4
        ptr = StrPtr(caption): CopyMemory info(12), ptr, 4
    #End If
    CopyMemory info(0), size, 4
    status = CredUIPromptForCredentialsW(info(0), StrPtr("ShiftReportExcel"), 0, 0, StrPtr(username), 256, StrPtr(password), 512, save, &H40082)
    If status = 1223 Then Exit Function
    Need status = 0, "Cannot open the Windows password dialog."
    SecretPrompt = Left$(password, InStr(1, password, vbNullChar) - 1): password = String$(512, vbNullChar)
End Function

Public Sub OpenProtectedProject()
    Dim selected As Variant, raw() As Byte, envelope As Object, config As Object, password As String
    Dim salt() As Byte, nonce() As Byte, tag() As Byte, key() As Byte, aad() As Byte, ciphertext() As Byte, plain() As Byte
    Dim bot As Object, chat As Object, member As Object, params As Object, binding As String, magic() As Byte
    selected = Application.GetOpenFilename("ShiftReport join (*.srjoin.json),*.srjoin.json", , "Open protected project")
    If VarType(selected) = vbBoolean Then Exit Sub
    On Error GoTo Failed
    raw = ReadBytes(CStr(selected)): Need ByteCount(raw) <= 1048576, "Join file exceeds 1 MiB."
    Set envelope = ParseJson(TextUtf8(raw))
    Need Txt(envelope, "format") = "ShiftReportJoin", "Select a protected ShiftReport join file."
    raw = Unbase64(Txt(envelope, "encryptedPayload")): Need ByteCount(raw) > 53, "Truncated join file."
    magic = Slice(raw, 0, 10): Need TextUtf8(magic) = "RUSHDJOIN1", "Unsupported join encryption."
    password = SecretPrompt(): If Len(password) = 0 Then Exit Sub
    Need Len(password) >= 10, "The join password must contain at least 10 characters."
    salt = Slice(raw, 10, 16): nonce = Slice(raw, 26, 12): tag = Slice(raw, ByteCount(raw) - 16, 16)
    ciphertext = Slice(raw, 38, ByteCount(raw) - 54): key = PasswordKey(password, salt): password = ""
    plain = Gcm(ciphertext, key, nonce, aad, tag, True): Erase key
    Set config = ParseJson(TextUtf8(plain)): Erase plain
    Need Num(config, "version") = 1 And Num(config, "chatId") < 0, "Invalid join configuration."
    Need InStr(Txt(config, "botToken"), ":") > 1, "Invalid bot configuration."
    If Len(Txt(config, "telegramDataKey")) Then
        key = Unbase64(Txt(config, "telegramDataKey")): Need ByteCount(key) = 32, "Invalid project encryption key."
    End If
    Need Not Flag(config, "encryptTelegram") Or ByteCount(key) = 32, "Encrypted project key is missing."
    binding = ProjectFingerprint(config)
    Need Len(StateText("project")) = 0 Or StateText("project") = binding, "This workbook belongs to another project/key/topic set. Use a separate workbook."
    Set params = Dict(): Set bot = Api("getMe", params, config)
    Need Flag(bot, "is_bot") And Txt(bot, "id") = Split(Txt(config, "botToken"), ":")(0), "The bot identity does not match."
    Put params, "chat_id", config("chatId"): Set chat = Api("getChat", params, config)
    Need Txt(chat, "id") = Txt(config, "chatId"), "Project group identity mismatch."
    Put params, "user_id", bot("id"): Set member = Api("getChatMember", params, config)
    Need Txt(member, "status") = "administrator" Or Txt(member, "status") = "creator", "The bot must be a group administrator."
    Need Flag(member, "can_pin_messages") Or Txt(member, "status") = "creator", "The bot needs pin permission."
    DisconnectProject: Set project = config: dataKey = key: Erase key
    StateSet "project", binding: SaveLocalWorkbook
    MsgBox "Project connected. Existing workbook tokens/groups were not used. Now sign in, then retrieve or explicitly import the original rows.", vbInformation
    Exit Sub
Failed:
    password = "": Erase key: Erase plain
    Need False, "Cannot open/validate the protected project. Check the file, password, bot rights and workbook project binding."
End Sub

Private Function Http(ByVal method As String, ByVal path As String) As Object
    Dim request As Object: Set request = CreateObject("WinHttp.WinHttpRequest.5.1")
    request.SetTimeouts 10000, 10000, 35000, 35000
    request.SetProxy 1 'Direct connection; never inherit a proxy carrying credentials.
    request.Option(6) = False 'No redirects.
    request.Option(9) = &H800 'TLS 1.2; never disable certificate validation.
    request.Open method, "https://api.telegram.org/" & path, False
    Set Http = request
End Function

Public Function Api(ByVal method As String, ByVal params As Object, Optional ByVal candidate As Object = Nothing) As Object
    Dim request As Object, config As Object, payload As Object, body() As Byte
    Dim status As Long, failure As Long
    On Error GoTo Failed
    If candidate Is Nothing Then Set config = project Else Set config = candidate
    Need Not config Is Nothing, "Open a protected project first."
    Set request = Http("POST", "bot" & Txt(config, "botToken") & "/" & method)
    request.SetRequestHeader "Content-Type", "application/json; charset=utf-8"
    body = Utf8(Json(params)): request.Send body: status = request.status
    Need Len(request.ResponseText) <= 16777216, "Telegram response exceeds the limit."
    Set payload = ParseJson(request.ResponseText)
    Need status = 200 And Flag(payload, "ok"), "Telegram rejected the request."
    Set Api = payload("result"): Exit Function
Failed:
    'Never surface WinHTTP descriptions: they may contain token-bearing URLs.
    Need False, "Telegram request failed or timed out. For a write, inspect the destination and receipt before retrying."
End Function

Public Function ReadIndex(ByRef indexId As String) As Object
    Dim params As Object, chat As Object, pinned As Object, text As String, raw() As Byte
    Set params = Dict(): Put params, "chat_id", CDbl(Cfg("chatId"))
    Set chat = Api("getChat", params)
    Need chat.Exists("pinned_message"), "No pinned project index. Create/publish the project in the app first."
    Set pinned = chat("pinned_message"): indexId = Txt(pinned, "message_id"): text = Txt(pinned, "text")
    If Left$(text, 7) = "SRMSG1:" Then
        raw = Unbase64(Mid$(text, 8)): raw = UnprotectData(raw, dataKey, "text"): raw = Gunzip(raw): text = TextUtf8(raw)
    Else
        Need ByteCount(dataKey) = 0, "Expected an encrypted project index."
    End If
    Set ReadIndex = ParseJson(text)
    Need Txt(ReadIndex, "kind") = "shift-report-sync-state" And Num(ReadIndex, "v") = 4, "The pin is not the current app index. It will not be replaced."
End Function

Public Function Download(ByVal fileId As String) As Byte()
    Dim params As Object, metadata As Object, path As String, request As Object, raw() As Byte
    Set params = Dict(): Put params, "file_id", fileId: Set metadata = Api("getFile", params)
    path = Txt(metadata, "file_path")
    Need Len(path) > 0 And Left$(path, 1) <> "/" And InStr(path, "..") = 0 And InStr(path, ":") = 0, "Invalid Telegram file path."
    On Error GoTo Failed
    Set request = Http("GET", "file/bot" & Cfg("botToken") & "/" & path): request.Send
    Need request.status = 200, "Download rejected."
    raw = request.ResponseBody: Need ByteCount(raw) <= 8388608, "Downloaded file exceeds 8 MiB."
    Download = UnprotectData(raw, dataKey, "file"): Exit Function
Failed:
    Need False, "Cannot download/decrypt the document. Verify connection and the original project key."
End Function

Public Function Upload(ByRef raw() As Byte, ByVal chat As String, ByVal topic As String, ByVal pdf As Boolean) As Object
    Dim boundary As String, head As String, tail As String, body() As Byte, a() As Byte, b() As Byte
    Dim request As Object, payload As Object, filename As String
    On Error GoTo Failed
    Need ByteCount(raw) <= 8388608, "Upload exceeds 8 MiB."
    boundary = "SRExcel" & Replace(NewId(), "-", "")
    filename = "ShiftReport.pdf"
    If Not pdf Then raw = ProtectData(raw, dataKey, "file"): filename = "ShiftReport.srdata"
    head = "--" & boundary & vbCrLf & "Content-Disposition: form-data; name=""chat_id""" & vbCrLf & vbCrLf & chat & vbCrLf
    head = head & "--" & boundary & vbCrLf & "Content-Disposition: form-data; name=""disable_notification""" & vbCrLf & vbCrLf & "true" & vbCrLf
    If Len(topic) > 0 And topic <> "0" Then head = head & "--" & boundary & vbCrLf & "Content-Disposition: form-data; name=""message_thread_id""" & vbCrLf & vbCrLf & topic & vbCrLf
    head = head & "--" & boundary & vbCrLf & "Content-Disposition: form-data; name=""document""; filename=""" & filename & """" & vbCrLf
    If pdf Then head = head & "Content-Type: application/pdf" Else head = head & "Content-Type: application/octet-stream"
    head = head & vbCrLf & vbCrLf: tail = vbCrLf & "--" & boundary & "--" & vbCrLf
    a = Utf8(head): b = Utf8(tail): body = Cat(a, raw): body = Cat(body, b)
    Set request = Http("POST", "bot" & Cfg("botToken") & "/sendDocument")
    request.SetRequestHeader "Content-Type", "multipart/form-data; boundary=" & boundary: request.Send body
    Need request.status = 200, "Upload rejected."
    Set payload = ParseJson(request.ResponseText): Need Flag(payload, "ok"), "Upload rejected."
    Set Upload = payload("result")
    Need Num(Upload, "message_id") > 0 And Upload.Exists("document"), "Telegram did not return the document identity."
    Exit Function
Failed:
    Need False, "Upload was not confirmed. The durable UNKNOWN receipt blocks a blind retry; inspect Telegram first."
End Function

Public Function IndexText(ByVal state As Object) As String
    Dim raw() As Byte, journal As Collection, text As String
    Do
        text = Json(state)
        If ByteCount(dataKey) Then
            raw = Utf8(text): raw = Gzip(raw): raw = ProtectData(raw, dataKey, "text"): text = "SRMSG1:" & Base64(raw)
        End If
        If Len(text) <= 4096 Then IndexText = text: Exit Function
        Need state.Exists("publicationJournal"), "Project index exceeds the message limit."
        Set journal = state("publicationJournal")
        Need journal.Count > 1, "Project index is too large; no unrelated field was dropped."
        journal.Remove journal.Count
    Loop
End Function

Public Sub WriteIndex(ByVal indexId As String, ByVal state As Object)
    Dim params As Object, ignored As Object, actual As Object, actualId As String, failure As Long
    Dim text As String: text = IndexText(state)
    Set params = Dict(): Put params, "chat_id", CDbl(Cfg("chatId")): Put params, "message_id", CDbl(indexId): Put params, "text", text
    On Error Resume Next
    Set ignored = Api("editMessageText", params): failure = Err.Number: Err.Clear
    On Error GoTo 0
    Set actual = ReadIndex(actualId)
    Need actualId = indexId And Pointers(actual) = Pointers(state), "Index update is unconfirmed or a concurrent publication won. Inspect the publication receipt."
End Sub

Public Function Pointers(ByVal state As Object) As String
    Pointers = Txt(state, "csvFileId") & "|" & Txt(state, "csvMessageId") & "|" & RegistryPointer(state)
End Function

Public Function RegistryPointer(ByVal state As Object) As String
    RegistryPointer = Txt(state, "doctorsFileId") & "|" & Txt(state, "doctorsMessageId") & "|" & Txt(state, "doctorsUpdatedAt")
End Function

Public Function FetchDoctors(ByVal state As Object) As Object
    Dim raw() As Byte
    Need Len(Txt(state, "doctorsFileId")) > 0, "Publish the current doctor registry from the app first."
    raw = Download(Txt(state, "doctorsFileId")): Set FetchDoctors = DecodeDoctors(TextUtf8(raw))
End Function

Public Sub LoginProject()
    Dim state As Object, doctors As Object, id As String, answer As Variant, d As Object, params As Object, ignored As Object
    Dim indexId As String, random() As Byte, code As String, n As Double, i As Long, typed As String
    Set state = ReadIndex(indexId): Set doctors = FetchDoctors(state): ShowDoctors doctors
    answer = Application.InputBox("Copy your doctor UUID from AppDoctors column A. Start the project bot privately before requesting a code.", "Verify clinician", Type:=2)
    If VarType(answer) = vbBoolean Then Exit Sub
    id = Trim$(CStr(answer)): Need doctors.Exists(id), "Choose an active published doctor UUID."
    Set d = doctors(id): Need Len(Txt(d, "id")) > 0, "This doctor has no linked Telegram account."
    actorId = "": expires = 0
    If pendingDoctor <> id Or EpochMs() - challengeAt >= 180000 Then
        Need challengeAt = 0 Or EpochMs() - challengeAt >= 30000, "Wait 30 seconds before requesting another code."
        random = RandomBytes(4)
        For i = 0 To 3: n = n * 256# + random(i): Next i
        n = n - Fix(n / 900000#) * 900000#: code = Format$(100000# + n, "0")
        random = RandomBytes(16): pendingSalt = Base64(random): pendingHash = Digest(pendingSalt & code)
        pendingDoctor = id: pendingTelegram = Txt(d, "id"): challengeAt = EpochMs(): challengeAttempts = 0
        Set params = Dict(): Put params, "chat_id", CDbl(pendingTelegram)
        Put params, "text", "ShiftReport Excel login code: " & code & vbLf & "Valid for 3 minutes. Do not share."
        Set ignored = Api("sendMessage", params): code = ""
    End If
    Do While challengeAttempts < 5 And EpochMs() - challengeAt < 180000
        answer = Application.InputBox("Enter the six-digit code sent privately by the bot.", "Verify clinician", Type:=2)
        If VarType(answer) = vbBoolean Then Exit Sub
        typed = Trim$(CStr(answer)): challengeAttempts = challengeAttempts + 1
        If Digest(pendingSalt & typed) = pendingHash Then
            actorId = id: actorTelegram = pendingTelegram: expires = EpochMs() + 1800000
            pendingHash = "": pendingSalt = "": Set d = Authorize(doctors)
            MsgBox "Verified clinician: " & Txt(d, "name"), vbInformation: Exit Sub
        End If
        MsgBox "Code incorrect or expired.", vbExclamation
    Loop
    Need False, "Code attempts/validity exhausted. Wait for expiry, then request another code."
End Sub

Public Function Authorize(ByVal doctors As Object) As Object
    Dim d As Object, member As Object, params As Object, status As String
    Need Len(actorId) > 0 And EpochMs() < expires, "Verify your clinician identity again. Sessions last 30 minutes."
    Need doctors.Exists(actorId), "The clinician is no longer in the published registry."
    Set d = doctors(actorId): Need Txt(d, "id") = actorTelegram, "The linked Telegram account changed. Sign in again."
    Set params = Dict(): Put params, "chat_id", CDbl(Cfg("chatId")): Put params, "user_id", CDbl(actorTelegram)
    Set member = Api("getChatMember", params): status = Txt(member, "status")
    Need status = "creator" Or status = "administrator" Or status = "member" Or (status = "restricted" And Flag(member, "is_member")), "The clinician is not a current project member."
    Set Authorize = d
End Function

Public Function Actor() As String
    Need Len(actorId) > 0 And EpochMs() < expires, "Verify clinician identity first."
    Actor = actorId
End Function
