---
title: "Configure policies wire format, version 1 (experimental)"
layout: default
canonical: "/puppetdb/latest/api/wire_format/configure_policies_v1.html"
---

# Configure policies wire format - v1 (experimental)

The v1 `configure policies` command allows the user to store policy information
in PuppetDB.

> *Note*: this is an experimental command, which might be altered or
> removed in a future release, and for the time being, PuppetDB
> exports will not include this information.

### Version

This is **version 1** of the configure policies command.

### Encoding

The command is serialized as JSON, which requires strict UTF-8 encoding.

### Main data type: Configure policies

```
{
  "producer_timestamp": <datetime>,
  "specification": <specification>
}
```

#### `producer_timestamp`

DateTime. The time of command submission from the Puppet Server to PuppetDB,
according to the clock on the Puppet Server.

`producer_timestamp` is optional but *highly* recommended. When provided, it is
used to determine the precedence between this command and other commands that
modify the same node. This field is provided by, and should thus reflect the
clock of, the Puppet Server.

When `producer_timestamp` is not provided, the PuppetDB server's local time is
used. If another command is received for a node while a non-timestamped
"deactivate node" command is pending processing, the results are *undefined*.

#### `specification`

The `specification` is a JSON object with zero or more key-value pairs. The
keys are `certname`s and the values are JSON Objects that specify the desired
policy configuration for that certname.

```
{
  <certname>: {
    "version": <policy_version>,
    "changed": <datetime>,
    "policies": [<policy_name>, ...]
  },
  ...
}
```

#### `policy_version`

A `policy_version` is a `<string>`.

#### `policy_name`

A `policy_name` is a `<string>`.

### Data type: `<string>`

A JSON string. Because the command is UTF-8, these must also be UTF-8.

### Data type: `<datetime>`

A JSON string representing a date and time (with time zone), formatted based on
the recommendations in ISO 8601. For example, for a UTC time, the string would be
formatted as `YYYY-MM-DDThh:mm:ss.sssZ`. For non-UTC time, the `Z` may be replaced
with `±hh:mm` to represent the specific timezone.
