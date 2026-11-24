import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const depsFile = path.join(here, 'out', 'maven-deps.txt')
const outFile = path.join(here, 'out', 'sca-osv.json')
const raw = fs.readFileSync(depsFile, 'utf8').replace(/\u001b\[[0-9;]*m/g, '')
const seen = new Map()
for (const line of raw.split(/\r?\n/)) {
  const match = line.match(/^\s*([^:\s]+):([^:]+):jar:([^:]+):(compile|runtime)/)
  if (!match) continue
  const key = `${match[1]}:${match[2]}:${match[3]}`
  seen.set(key, { group: match[1], artifact: match[2], version: match[3], scope: match[4] })
}

const coords = [...seen.values()]
const queries = coords.map((item) => ({
  package: { name: `${item.group}:${item.artifact}`, ecosystem: 'Maven' },
  version: item.version,
}))

async function queryBatch(batch) {
  const response = await fetch('https://api.osv.dev/v1/querybatch', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ queries: batch }),
  })
  if (!response.ok) {
    throw new Error(`OSV ${response.status}: ${await response.text()}`)
  }
  return response.json()
}

const results = []
for (let i = 0; i < queries.length; i += 40) {
  const chunk = queries.slice(i, i + 40)
  const payload = await queryBatch(chunk)
  results.push(...(payload.results || []))
}

const findings = []
results.forEach((result, index) => {
  const vulns = result.vulns || []
  if (!vulns.length) return
  const coord = coords[index]
  findings.push({
    package: `${coord.group}:${coord.artifact}`,
    version: coord.version,
    scope: coord.scope,
    vulns: vulns.map((item) => ({ id: item.id, modified: item.modified })),
  })
})

fs.writeFileSync(outFile, JSON.stringify({ scanned: coords.length, vulnerable: findings.length, findings }, null, 2))
console.log(`OSV Maven: ${coords.length} artefactos, ${findings.length} con CVE/GHSA`)
for (const item of findings) {
  console.log(`- ${item.package}:${item.version} (${item.vulns.map((v) => v.id).join(', ')})`)
}
