import { useState } from 'react'
import axios from 'axios'
import './App.css'

function App() {
  const [account, setAccount] = useState(null)
  const [cid, setCid] = useState(null)
  const [txHash, setTxHash] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState(null)

  const connectWallet = async () => {
    try {
      if (!window.ethereum) {
        alert('Install MetaMask')
        return
      }
      const accounts = await window.ethereum.request({ method: 'eth_requestAccounts' })
      setAccount(accounts[0])
    } catch (e) {
      setError(String(e))
    }
  }

  const uploadDataset = async (event) => {
    event.preventDefault()
    const fileInput = event.target.file
    if (!fileInput?.files?.[0]) return
    setUploading(true)
    setError(null)
    setCid(null)
    setTxHash(null)
    try {
      const formData = new FormData()
      formData.append('file', fileInput.files[0])
      formData.append('license', 'CC-BY')
      const base = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000'
      const res = await axios.post(`${base}/upload`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      setCid(res.data.cid)
      setTxHash(res.data.tx_hash)
    } catch (e) {
      setError(e?.response?.data?.detail || String(e))
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="App" style={{ maxWidth: 720, margin: '2rem auto', padding: '1rem' }}>
      <h1>ProofMind Dashboard</h1>
      {!account ? (
        <button onClick={connectWallet}>Connect Wallet</button>
      ) : (
        <>
          <p>Connected: {account}</p>
          <form onSubmit={uploadDataset}>
            <input type="file" name="file" accept=".csv" />
            <button type="submit" disabled={uploading}>
              {uploading ? 'Uploading…' : 'Upload & Verify'}
            </button>
          </form>
        </>
      )}
      {cid && (
        <p>
          Stored CID: <code>{cid}</code>
          <br /> TX: <code>{txHash}</code>
        </p>
      )}
      {error && (
        <p style={{ color: 'crimson' }}>Error: {error}</p>
      )}
    </div>
  )
}

export default App
