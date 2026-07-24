# Integration-testing helpers

When creating an environment you can attach two helper containers so integrations
and email can be exercised without touching real external systems. When present,
they appear in a **Test Addons** card on the Containers tab.

## Mock receiver

Captures outbound HTTP integrations. From inside Maximo, point publish channels /
endpoints at **`http://mock:8085`** (the container also answers to the alias
`mock-receiver`, so existing endpoint rows pointing at `http://mock-receiver:8085`
resolve too). The captured requests are browsable at the **Mock UI**
(`http://<host>:<mockPort>/__mock/`), which also has a rules editor for scripting
templated responses.

## SMTP capture (Mailpit)

Captures outbound email. Point Maximo's SMTP at **`smtp:1025`**; read everything
it sends in the **Inbox UI** (Mailpit, `http://<host>:<mailpitUiPort>`). No mail
ever leaves the host.

Enable either from the **New Build** dialog (**Include mock receiver** / **Include
SMTP server**). If you use static ports, set the corresponding Mock/SMTP/Mailpit-UI
ports on the template first.
