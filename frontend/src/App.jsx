import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, getToken, jsonBody, saveToken } from './api.js'

const statusLabels = {
  PENDENTE_ANALISE: 'Aguardando análise',
  EM_ANALISE: 'Em análise',
  APROVADO: 'Aprovado',
  REPROVADO: 'Não aprovado',
  AGUARDANDO_EXECUCAO: 'Aprovado · aguardando contrato',
  CONTRATO_EXECUTADO: 'Contrato executado',
  CANCELADO: 'Cancelado',
}

const localDate = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const today = () => localDate(new Date())
const plusDays = (days) => {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return localDate(date)
}

function App() {
  const [user, setUser] = useState(null)
  const [view, setView] = useState('overview')
  const [requests, setRequests] = useState([])
  const [profile, setProfile] = useState(null)
  const [banks, setBanks] = useState([])
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [dialog, setDialog] = useState(null)
  const [loginArrival, setLoginArrival] = useState(false)
  const [pageAnimation, setPageAnimation] = useState(false)
  const visitedPages = useRef(new Set())
  const isClient = user?.role === 'CLIENT'

  function navigateTo(nextView) {
    setPageAnimation(!visitedPages.current.has(nextView))
    visitedPages.current.add(nextView)
    setView(nextView)
  }

  const loadWorkspace = useCallback(async (activeUser = user) => {
    if (!activeUser) return
    setBusy(true)
    setError('')
    try {
      if (activeUser.role === 'CLIENT') {
        const [client, items] = await Promise.all([api('/client/profile'), api('/requests')])
        setProfile(client)
        setRequests(items)
        setBanks([])
      } else {
        const [items, bankOptions] = await Promise.all([api('/agent/requests'), api('/agent/requests/banks')])
        setRequests(items)
        setBanks(bankOptions)
        setProfile(null)
      }
    } catch (cause) {
      setError(cause.message)
    } finally {
      setBusy(false)
    }
  }, [user])

  useEffect(() => {
    if (!getToken()) return
    api('/auth/me').then((activeUser) => {
      setUser(activeUser)
      visitedPages.current.add('overview')
      setPageAnimation(true)
    }).catch(() => {
      saveToken(null)
      setUser(null)
    })
  }, [])

  useEffect(() => {
    if (!loginArrival) return undefined
    const timer = window.setTimeout(() => setLoginArrival(false), 1450)
    return () => window.clearTimeout(timer)
  }, [loginArrival])

  useEffect(() => {
    if (user) loadWorkspace(user)
  }, [user, loadWorkspace])

  const showNotice = (message) => {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 3600)
  }

  async function authenticate(path, payload) {
    setBusy(true)
    setError('')
    try {
      const result = await api(path, { method: 'POST', body: jsonBody(payload) })
      saveToken(result.token)
      setUser({ userId: result.userId, username: result.username, role: result.role, displayName: result.displayName })
      visitedPages.current.clear()
      visitedPages.current.add('overview')
      setView('overview')
      setPageAnimation(true)
      setLoginArrival(true)
    } catch (cause) {
      setError(cause.message)
    } finally {
      setBusy(false)
    }
  }

  async function signOut() {
    try { await api('/auth/logout', { method: 'POST' }) } catch { /* expiração local continua válida */ }
    saveToken(null)
    setUser(null)
    setProfile(null)
    setRequests([])
    visitedPages.current.clear()
    setPageAnimation(false)
  }

  async function saveRental(payload, requestId) {
    const result = await api(requestId ? `/requests/${requestId}` : '/requests', {
      method: requestId ? 'PUT' : 'POST', body: jsonBody(payload),
    })
    setDialog(null)
    setError('')
    setRequests((current) => requestId
      ? current.map((item) => item.id === requestId ? result : item)
      : [result, ...current])
    showNotice(requestId ? 'Pedido atualizado.' : 'Pedido enviado para análise.')
    return result
  }

  async function cancelRental(item) {
    if (!window.confirm(`Cancelar o pedido ${item.id.slice(0, 8)}?`)) return
    try {
      await api(`/requests/${item.id}`, { method: 'DELETE' })
      await loadWorkspace()
      showNotice('Pedido cancelado.')
    } catch (cause) { setError(cause.message) }
  }

  async function beginReview(item) {
    try {
      await api(`/agent/requests/${item.id}/start-analysis`, { method: 'PATCH' })
      await loadWorkspace()
      showNotice('Análise iniciada.')
    } catch (cause) { setError(cause.message) }
  }

  async function assess(item, approved) {
    const opinion = window.prompt(approved ? 'Parecer financeiro para aprovação:' : 'Motivo do parecer desfavorável:')
    if (!opinion?.trim()) return
    try {
      await api(`/agent/requests/${item.id}/assessment`, {
        method: 'POST', body: jsonBody({ opinion, approved }),
      })
      await loadWorkspace()
      showNotice(approved ? 'Pedido aprovado e enviado para execução do contrato.' : 'Parecer registrado.')
    } catch (cause) { setError(cause.message) }
  }

  async function executeContract(item, payload) {
    try {
      await api(`/agent/requests/${item.id}/execute`, { method: 'POST', body: jsonBody(payload) })
      setDialog(null)
      await loadWorkspace()
      showNotice('Contrato executado.')
    } catch (cause) { setError(cause.message); throw cause }
  }

  async function saveProfile(payload) {
    try {
      const result = await api('/client/profile', { method: 'PUT', body: jsonBody(payload) })
      setProfile(result)
      setUser((current) => ({ ...current, displayName: result.name }))
      showNotice('Cadastro atualizado.')
    } catch (cause) { setError(cause.message) }
  }

  async function deactivateProfile() {
    if (!window.confirm('Desativar seu cadastro? Você será desconectado e não poderá entrar novamente.')) return
    try {
      await api('/client/profile', { method: 'DELETE' })
      saveToken(null)
      setUser(null)
      showNotice('Cadastro desativado.')
    } catch (cause) { setError(cause.message) }
  }

  const stats = useMemo(() => ({
    total: requests.length,
    waiting: requests.filter((item) => ['PENDENTE_ANALISE', 'EM_ANALISE'].includes(item.status)).length,
    approved: requests.filter((item) => ['AGUARDANDO_EXECUCAO', 'CONTRATO_EXECUTADO'].includes(item.status)).length,
  }), [requests])

  if (!user) return <AuthScreen onAuthenticate={authenticate} busy={busy} error={error} />

  return (
    <div className={`app-shell ${loginArrival ? 'is-arriving' : ''}`}>
      {loginArrival && <div className="login-drive" aria-hidden="true"><div className="drive-streak"/><CarIllustration /></div>}
      <aside className={`sidebar ${loginArrival ? 'sidebar-arriving' : ''}`}>
        <div className="brand"><img className="brand-mark" src="/rota-symbol.svg" alt="" /><span>rota<span className="brand-dot">.</span><small>GESTÃO DE ALUGUÉIS</small></span></div>
        <div className="side-caption">MENU PRINCIPAL</div>
        <nav className="side-nav">
          <button className={view === 'overview' ? 'active' : ''} onClick={() => navigateTo('overview')}><Icon name="grid" />Visão geral</button>
          <button className={view === 'requests' ? 'active' : ''} onClick={() => navigateTo('requests')}><Icon name="car" />{isClient ? 'Meus pedidos' : 'Pedidos' }<span className="nav-count">{requests.length}</span></button>
          {isClient && <button className={view === 'profile' ? 'active' : ''} onClick={() => navigateTo('profile')}><Icon name="user" />Meu cadastro</button>}
          <button className={view === 'assistant' ? 'active' : ''} onClick={() => navigateTo('assistant')}><Icon name="message" />Conversar com agente</button>
        </nav>
        <div className="sidebar-bottom">
          <div className="help-card"><span className="help-icon">✳</span><strong>Precisa de ajuda?</strong><p>Consulte seu agente para saber mais sobre uma análise.</p></div>
          <div className="profile-mini"><div className="avatar">{user.displayName?.slice(0, 1)?.toUpperCase() || 'U'}</div><div className="profile-mini-copy"><strong>{user.displayName}</strong><small>{roleLabel(user.role)}</small></div><button className="icon-button" aria-label="Sair" onClick={signOut}><Icon name="logout" /></button></div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="breadcrumb">Workspace <span>/</span> {viewLabel(view, isClient)}</div>
          <div className="topbar-right"><span className="secure-label"><span />Conexão segura</span><button className="top-logout" type="button" onClick={signOut}><Icon name="logout" /><span>Sair</span></button><div className="top-avatar">{user.displayName?.slice(0, 1)?.toUpperCase()}</div></div>
        </header>
        <section className="content-area">
          {error && <div className="alert alert-error"><span>!</span>{error}<button onClick={() => setError('')}>×</button></div>}
          {notice && <div className="toast"><span>✓</span>{notice}</div>}
          <div key={view} className={pageAnimation ? 'page-enter' : 'page-content'}>
            {view === 'profile' && isClient ? (
              <ProfilePage profile={profile} onSave={saveProfile} onDeactivate={deactivateProfile} busy={busy} />
            ) : view === 'assistant' ? (
              <AssistantPage user={user} />
            ) : view === 'requests' ? (
              <RequestsPage
                user={user} requests={requests} busy={busy} onNew={() => setDialog({ kind: 'new' })}
                onEdit={(item) => setDialog({ kind: 'edit', item })} onCancel={cancelRental}
                onReview={beginReview} onAssess={assess} onModifyAgent={(item) => setDialog({ kind: 'agent-edit', item })}
                onExecute={(item) => setDialog({ kind: 'execute', item })}
              />
            ) : (
              <OverviewPage user={user} profile={profile} requests={requests} stats={stats} busy={busy}
                onNew={() => setDialog({ kind: 'new' })} onRequests={() => navigateTo('requests')} onProfile={() => navigateTo('profile')} />
            )}
          </div>
        </section>
      </main>

      {dialog?.kind === 'new' && <Modal wide title="Escolha seu próximo carro" onClose={() => setDialog(null)}><RentalForm onSubmit={(data) => saveRental(data)} onCancel={() => setDialog(null)} /></Modal>}
      {dialog?.kind === 'edit' && <Modal wide title="Editar pedido de aluguel" onClose={() => setDialog(null)}><RentalForm item={dialog.item} onSubmit={(data) => saveRental(data, dialog.item.id)} onCancel={() => setDialog(null)} /></Modal>}
      {dialog?.kind === 'agent-edit' && <Modal title="Atualizar período do pedido" onClose={() => setDialog(null)}><AgentEditForm item={dialog.item} onSubmit={async (data) => {
        try { await api(`/agent/requests/${dialog.item.id}`, { method: 'PUT', body: jsonBody(data) }); setDialog(null); await loadWorkspace(); showNotice('Dados atualizados.') }
        catch (cause) { setError(cause.message) }
      }} onCancel={() => setDialog(null)} /></Modal>}
      {dialog?.kind === 'execute' && <Modal title="Executar contrato" onClose={() => setDialog(null)}><ExecuteForm banks={banks} onSubmit={(data) => executeContract(dialog.item, data)} onCancel={() => setDialog(null)} /></Modal>}
    </div>
  )
}

function AuthScreen({ onAuthenticate, busy, error }) {
  const [mode, setMode] = useState('login')
  const [kind, setKind] = useState('client')
  const [form, setForm] = useState({})
  const [employments, setEmployments] = useState([{ employer: '', income: '' }])
  const set = (field, value) => setForm((current) => ({ ...current, [field]: value }))
  const submit = (event) => {
    event.preventDefault()
    if (mode === 'login') return onAuthenticate('/auth/login', { username: form.username, password: form.password })
    if (kind === 'agent') return onAuthenticate('/auth/register-agent', {
      username: form.username, password: form.password, type: form.agentType, entityName: form.entityName, inviteCode: form.inviteCode,
    })
    return onAuthenticate('/auth/register', {
      username: form.username, password: form.password, rg: form.rg, cpf: form.cpf, name: form.name,
      address: form.address, profession: form.profession,
      employments: employments.filter((item) => item.employer && item.income).map((item) => ({ ...item, income: Number(item.income) })),
    })
  }
  const register = mode === 'register'
  return (
    <div className="auth-layout">
      <section className="auth-brand-panel">
        <div className="brand brand-light"><img className="brand-mark" src="/rota-symbol.svg" alt="" /><span>rota<span className="brand-dot">.</span><small>GESTÃO DE ALUGUÉIS</small></span></div>
        <div className="hero-copy"><div className="eyebrow"><span />ALUGUEL SEM COMPLICAÇÃO</div><h1>Seu próximo caminho começa aqui.</h1><p>Uma plataforma simples para acompanhar pedidos, análises e contratos de aluguel de automóveis.</p><div className="hero-tags"><span>01 · Solicite</span><span>02 · Acompanhe</span><span>03 · Dirija</span></div></div>
        <div className="hero-car-wrap"><div className="hero-orbit orbit-one"/><div className="hero-orbit orbit-two"/><CarIllustration /></div>
        <div className="panel-foot">PLATAFORMA DE GESTÃO DE ALUGUÉIS <span>●</span> ACESSO PROTEGIDO</div>
      </section>
      <section className="auth-form-panel">
        <div className="auth-topline"><span>PORTAL DO CLIENTE E AGENTE</span><span className="live-dot">● Online</span></div>
        <div className="auth-card">
          <div className="auth-heading"><div className="eyebrow muted">BEM-VINDO À ROTA</div><h2>{register ? 'Crie seu acesso' : 'Entre na sua conta'}</h2><p>{register ? 'Preencha seus dados para começar.' : 'Informe seus dados para continuar.'}</p></div>
          <div className="auth-switch"><button className={mode === 'login' ? 'selected' : ''} onClick={() => { setMode('login'); setForm({}) }}>Entrar</button><button className={mode === 'register' ? 'selected' : ''} onClick={() => { setMode('register'); setForm({}) }}>Criar conta</button></div>
          {register && <div className="kind-switch"><button className={kind === 'client' ? 'selected' : ''} onClick={() => setKind('client')}>Cliente</button><button className={kind === 'agent' ? 'selected' : ''} onClick={() => setKind('agent')}>Agente</button></div>}
          {error && <div className="inline-error">{error}</div>}
          <form onSubmit={submit} className="form-stack">
            {register && kind === 'client' && <>
              <div className="field-grid"><Field label="Nome completo" value={form.name} onChange={(v) => set('name', v)} required /><Field label="CPF" value={form.cpf} onChange={(v) => set('cpf', v)} required /><Field label="RG" value={form.rg} onChange={(v) => set('rg', v)} required /><Field label="Profissão" value={form.profession} onChange={(v) => set('profession', v)} required /></div>
              <Field label="Endereço completo" value={form.address} onChange={(v) => set('address', v)} required />
              <div className="employment-register"><div className="section-line"><strong>Renda e empregador</strong><span>{employments.length}/3</span></div>
                {employments.map((item, index) => <div className="field-grid employment-row" key={index}><Field label={`Empregador ${index + 1}`} value={item.employer} onChange={(v) => setEmployments((current) => current.map((row, i) => i === index ? { ...row, employer: v } : row))} /><Field label="Rendimento mensal (R$)" type="number" min="0.01" step="0.01" value={item.income} onChange={(v) => setEmployments((current) => current.map((row, i) => i === index ? { ...row, income: v } : row))} /></div>)}
                {employments.length < 3 && <button type="button" className="text-button" onClick={() => setEmployments((current) => [...current, { employer: '', income: '' }])}>+ Adicionar vínculo</button>}
              </div>
            </>}
            {register && kind === 'agent' && <>
              <Field label="Empresa ou banco" value={form.entityName} onChange={(v) => set('entityName', v)} required />
              <label className="field"><span>Tipo de agente</span><select value={form.agentType || 'COMPANY'} onChange={(e) => set('agentType', e.target.value)}><option value="COMPANY">Empresa</option><option value="BANK">Banco</option></select></label>
              <Field label="Código de convite" value={form.inviteCode} onChange={(v) => set('inviteCode', v)} required />
            </>}
            <div className="field-grid"><Field label="Usuário" value={form.username} onChange={(v) => set('username', v)} autoComplete="username" required /><Field label="Senha" type="password" value={form.password} onChange={(v) => set('password', v)} autoComplete={register ? 'new-password' : 'current-password'} required /></div>
            <button className="button button-primary button-full" disabled={busy}>{busy ? <span className="spinner" /> : register ? 'Criar acesso' : 'Entrar na plataforma'} <span>→</span></button>
          </form>
          <div className="auth-security"><span>♧</span> Seus dados são protegidos e acessíveis apenas por usuários autorizados.</div>
        </div>
        <div className="auth-footer">© 2026 Rota • Projeto acadêmico de gestão de aluguéis</div>
      </section>
    </div>
  )
}

function OverviewPage({ user, profile, requests, stats, busy, onNew, onRequests, onProfile }) {
  const client = user.role === 'CLIENT'
  const recent = requests.slice(0, 4)
  return <>
    <div className="page-heading"><div><div className="eyebrow muted">{client ? 'ÁREA DO CLIENTE' : 'PAINEL DO AGENTE'}</div><h1>Olá, {user.displayName?.split(' ')[0]} <span className="wave">✳</span></h1><p>{client ? 'Aqui está um resumo dos seus pedidos de aluguel.' : 'Acompanhe e avalie os pedidos enviados para análise.'}</p></div>{client && <button className="button button-primary" onClick={onNew}><span className="plus">＋</span> Novo pedido</button>}</div>
    {client && <div className="welcome-banner"><div className="welcome-copy"><div className="banner-kicker">PRONTO PARA A PRÓXIMA VIAGEM?</div><h2>Peça seu aluguel<br/>em poucos passos.</h2><p>Informe o veículo e as datas. Você acompanha cada atualização por aqui.</p><button className="button button-light" onClick={onNew}>Solicitar aluguel <span>→</span></button></div><div className="banner-art"><div className="banner-sun"/><CarIllustration compact /></div><div className="banner-index">ROTA / 01</div></div>}
    <div className="stats-grid">
      <StatCard label={client ? 'Meus pedidos' : 'Pedidos recebidos'} value={busy ? '—' : stats.total} icon="car" accent="blue" hint={client ? 'em todos os períodos' : 'no sistema'} />
      <StatCard label="Em acompanhamento" value={busy ? '—' : stats.waiting} icon="clock" accent="amber" hint="aguardando parecer" />
      <StatCard label={client ? 'Aprovados / contratos' : 'Aprovados'} value={busy ? '—' : stats.approved} icon="check" accent="green" hint="próxima etapa disponível" />
    </div>
    <div className="dashboard-grid">
      <section className="panel recent-panel"><div className="panel-header"><div><div className="eyebrow muted">ACOMPANHAMENTO</div><h3>{client ? 'Pedidos recentes' : 'Fila de pedidos'}</h3></div><button className="link-button" onClick={onRequests}>Ver todos <span>→</span></button></div>
        {recent.length ? <div className="recent-list">{recent.map((item) => <RequestRow key={item.id} item={item} compact />)}</div> : <EmptyState title={client ? 'Nenhum pedido por enquanto' : 'A fila está vazia'} description={client ? 'Crie seu primeiro pedido para iniciar o acompanhamento.' : 'Os pedidos de aluguel aparecerão aqui.'} />}
      </section>
      <section className="panel profile-summary"><div className="panel-header"><div><div className="eyebrow muted">{client ? 'SEU CADASTRO' : 'SUA CONTA'}</div><h3>{client ? 'Dados pessoais' : 'Perfil de agente'}</h3></div><div className="summary-icon"><Icon name="user" /></div></div>
        {client && profile ? <><div className="summary-person"><div className="avatar avatar-large">{profile.name.slice(0, 1).toUpperCase()}</div><div><strong>{profile.name}</strong><small>{profile.profession}</small></div></div><div className="summary-detail"><span>CPF</span><strong>{maskCpf(profile.cpf)}</strong></div><div className="summary-detail"><span>Vínculos de renda</span><strong>{profile.employments?.length || 0} de 3</strong></div><button className="button button-outline button-full" onClick={onProfile}>Ver meu cadastro <span>→</span></button></> : <><div className="summary-person"><div className="avatar avatar-large">{user.displayName?.slice(0, 1).toUpperCase()}</div><div><strong>{user.displayName}</strong><small>{roleLabel(user.role)}</small></div></div><div className="summary-detail"><span>Usuário</span><strong>{user.username}</strong></div><div className="agent-tip"><span>✦</span> Mantenha os pareceres claros para facilitar o acompanhamento do cliente.</div></>}
      </section>
    </div>
  </>
}

function RequestsPage({ user, requests, busy, onNew, onEdit, onCancel, onReview, onAssess, onModifyAgent, onExecute }) {
  const client = user.role === 'CLIENT'
  return <>
    <div className="page-heading"><div><div className="eyebrow muted">{client ? 'ÁREA DO CLIENTE' : 'ANÁLISE FINANCEIRA'}</div><h1>{client ? 'Meus pedidos' : 'Pedidos de aluguel'}</h1><p>{client ? 'Consulte o andamento e os detalhes de cada solicitação.' : 'Revise os dados e registre o parecer de cada solicitação.'}</p></div>{client && <button className="button button-primary" onClick={onNew}><span className="plus">＋</span> Novo pedido</button>}</div>
    <div className="request-toolbar"><span>{requests.length} {requests.length === 1 ? 'pedido encontrado' : 'pedidos encontrados'}</span><span className="toolbar-right"><span className="secure-label"><span />Atualizado agora</span></span></div>
    {busy ? <div className="loading-box"><span className="spinner spinner-dark"/>Carregando pedidos...</div> : requests.length ? <div className="request-list">{requests.map((item) => <RequestCard key={item.id} item={item} client={client} onEdit={onEdit} onCancel={onCancel} onReview={onReview} onAssess={onAssess} onModifyAgent={onModifyAgent} onExecute={onExecute} />)}</div> : <div className="panel empty-panel"><EmptyState title={client ? 'Você ainda não tem pedidos' : 'Nenhum pedido para analisar'} description={client ? 'Crie uma solicitação de aluguel e acompanhe a análise por aqui.' : 'Quando um cliente enviar uma solicitação, ela aparecerá nesta lista.'} action={client ? <button className="button button-primary" onClick={onNew}>Criar pedido</button> : null} /></div>}
  </>
}

function ProfilePage({ profile, onSave, onDeactivate, busy }) {
  const [form, setForm] = useState(null)
  useEffect(() => {
    if (profile) setForm({ ...profile, employments: (profile.employments || []).map((item) => ({ ...item })) })
  }, [profile])
  if (!form) return <div className="loading-box"><span className="spinner spinner-dark"/>Carregando cadastro...</div>
  const set = (key, value) => setForm((current) => ({ ...current, [key]: value }))
  const updateEmployment = (index, key, value) => set('employments', form.employments.map((item, i) => i === index ? { ...item, [key]: value } : item))
  const submit = (event) => { event.preventDefault(); onSave({ rg: form.rg, cpf: form.cpf, name: form.name, address: form.address, profession: form.profession, employments: form.employments.map(({ employer, income }) => ({ employer, income: Number(income) })) }) }
  return <>
    <div className="page-heading"><div><div className="eyebrow muted">DADOS DO CLIENTE</div><h1>Meu cadastro</h1><p>Mantenha seus dados atualizados para a análise financeira.</p></div><button className="button button-danger-soft" onClick={onDeactivate}>Desativar cadastro</button></div>
    <form className="panel profile-form" onSubmit={submit}><div className="form-section-head"><span className="section-number">01</span><div><h3>Identificação e contato</h3><p>Informações pessoais do contratante.</p></div></div><div className="field-grid profile-fields"><Field label="Nome completo" value={form.name} onChange={(v) => set('name', v)} required /><Field label="CPF" value={form.cpf} onChange={(v) => set('cpf', v)} required /><Field label="RG" value={form.rg} onChange={(v) => set('rg', v)} required /><Field label="Profissão" value={form.profession} onChange={(v) => set('profession', v)} required /><div className="span-2"><Field label="Endereço" value={form.address} onChange={(v) => set('address', v)} required /></div></div>
      <div className="form-section-head section-spaced"><span className="section-number">02</span><div><h3>Emprego e renda</h3><p>Cadastre até três fontes de rendimento.</p></div><span className="count-pill">{form.employments.length} / 3</span></div>
      <div className="employment-editor">{form.employments.map((item, index) => <div className="employment-edit-row" key={item.id || index}><div className="employment-index">0{index + 1}</div><Field label="Empregador" value={item.employer} onChange={(v) => updateEmployment(index, 'employer', v)} required /><Field label="Rendimento mensal (R$)" type="number" min="0.01" step="0.01" value={item.income} onChange={(v) => updateEmployment(index, 'income', v)} required /><button className="icon-button remove-button" type="button" title="Remover vínculo" onClick={() => set('employments', form.employments.filter((_, i) => i !== index))}>×</button></div>)}</div>
      {form.employments.length < 3 && <button className="text-button add-employment" type="button" onClick={() => set('employments', [...form.employments, { employer: '', income: '' }])}>＋ Adicionar vínculo</button>}
      <div className="form-actions"><span>Usuário: <strong>{profile.username}</strong></span><button className="button button-primary" disabled={busy}>Salvar alterações <span>→</span></button></div>
    </form>
  </>
}

function AssistantPage({ user }) {
  const [messages, setMessages] = useState([{ role: 'assistant', content: `Olá${user.displayName ? `, ${user.displayName.split(' ')[0]}` : ''}! Sou o assistente virtual da Rota. Posso ajudar com dúvidas sobre a frota, valores e como acompanhar seu pedido.` }])
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [assistantStatus, setAssistantStatus] = useState(null)
  const endRef = useRef(null)

  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }) }, [messages, sending])
  useEffect(() => {
    let active = true
    api('/assistant/status').then((status) => { if (active) setAssistantStatus(status) })
      .catch(() => { if (active) setAssistantStatus({ configured: false, model: null }) })
    return () => { active = false }
  }, [])

  async function sendMessage(rawText) {
    const content = rawText.trim()
    if (!content || sending) return
    setError('')
    setDraft('')
    const conversation = [...messages, { role: 'user', content }]
    setMessages(conversation)
    setSending(true)
    try {
      const result = await api('/assistant/chat', {
        method: 'POST',
        body: jsonBody({ messages: conversation.slice(-12).map(({ role, content: message }) => ({ role, content: message })) }),
      })
      setMessages([...conversation, { role: 'assistant', content: result.reply }])
    } catch (cause) {
      setError(cause.message)
    } finally {
      setSending(false)
    }
  }

  const suggestions = ['Como funciona o aluguel?', 'Quais carros estão na frota?', 'Como acompanho meu pedido?']
  return <>
    <div className="page-heading"><div><div className="eyebrow muted">ATENDIMENTO ROTA</div><h1>Converse com o agente</h1><p>Tire dúvidas sobre veículos, valores e o fluxo de aluguel.</p></div><span className={`assistant-online ${assistantStatus && !assistantStatus.configured ? 'assistant-not-configured' : ''}`}><i />{assistantStatus === null ? 'Verificando configuração' : assistantStatus.configured ? 'Chave carregada' : 'Configuração necessária'}</span></div>
    <section className="assistant-workspace">
      <div className="assistant-about"><div className="assistant-avatar"><span>✦</span></div><div><span className="eyebrow muted">ASSISTENTE VIRTUAL</span><h2>Rota Assistente</h2><p>Ajuda rápida para você seguir viagem com tranquilidade.</p></div><div className="assistant-about-rule"/><div className="assistant-capability"><span>01</span><div><strong>Frota e diárias</strong><small>Consulte modelos, categorias e valores de referência.</small></div></div><div className="assistant-capability"><span>02</span><div><strong>Etapas do aluguel</strong><small>Entenda a solicitação e o acompanhamento do pedido.</small></div></div><div className="assistant-note"><Icon name="check" /> Disponibilidade confirmada após escolher as datas.</div></div>
      <div className="assistant-chat">
        <header className="assistant-chat-head"><div className="assistant-avatar assistant-avatar-small"><span>✦</span></div><div><strong>Rota Assistente</strong><small>{assistantStatus?.model || 'Assistente de locação'}</small></div><span className={`assistant-chat-state ${assistantStatus?.configured ? 'configured' : ''}`}>{assistantStatus === null ? 'Aguarde' : assistantStatus.configured ? 'OpenAI compatible' : 'Sem chave'}</span></header>
        <div className="chat-messages" aria-live="polite" aria-label="Conversa com o agente">
          {messages.map((message, index) => <div className={`chat-message ${message.role === 'user' ? 'from-user' : 'from-assistant'}`} key={`${index}-${message.role}`}><div className="chat-message-avatar">{message.role === 'user' ? user.displayName?.slice(0, 1)?.toUpperCase() || 'U' : '✦'}</div><div className="chat-message-body"><span>{message.role === 'user' ? 'Você' : 'Rota Assistente'}</span><p>{message.content}</p></div></div>)}
          {sending && <div className="chat-message from-assistant"><div className="chat-message-avatar">✦</div><div className="chat-message-body"><span>Rota Assistente</span><p className="typing-indicator"><i/><i/><i/></p></div></div>}
          <div ref={endRef} />
        </div>
        {messages.length === 1 && <div className="chat-suggestions">{suggestions.map((suggestion) => <button type="button" key={suggestion} disabled={sending} onClick={() => sendMessage(suggestion)}>{suggestion}<span>↗</span></button>)}</div>}
        {assistantStatus && !assistantStatus.configured && <div className="chat-error" role="status">Configure OPENAI_API_KEY no arquivo .env da raiz e reinicie o ambiente para ativar o chat.</div>}
        {error && <div className="chat-error" role="alert">{error}</div>}
        <form className="chat-composer" onSubmit={(event) => { event.preventDefault(); sendMessage(draft) }}><textarea rows="2" maxLength="2000" aria-label="Escreva sua mensagem" placeholder="Escreva sua pergunta..." value={draft} onChange={(event) => setDraft(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); sendMessage(draft) } }} /><div><small>Enter para enviar · Shift + Enter para nova linha</small><button className="button button-primary" disabled={sending || !draft.trim()} aria-label="Enviar mensagem">{sending ? <span className="spinner" /> : <><span>Enviar</span><Icon name="send" /></>}</button></div></form>
        <p className="chat-disclaimer">As mensagens são enviadas ao provedor de IA para gerar respostas; não informe dados pessoais ou senhas. Confirme a disponibilidade na busca de veículos.</p>
      </div>
    </section>
  </>
}

function RequestCard({ item, client, onEdit, onCancel, onReview, onAssess, onModifyAgent, onExecute }) {
  const badgeClass = statusClass(item.status)
  return <article className="request-card">
    <div className="request-card-top"><div className="request-id-wrap"><div className="car-thumb">{item.imageUrl ? <img src={item.imageUrl} alt="" /> : <Icon name="car" />}</div><div><span className="request-ref">PEDIDO / {item.id.slice(0, 8).toUpperCase()}</span><h3>{item.make} {item.model}</h3><span className="request-meta">{item.year} <i>•</i> {item.category || 'Veículo'}{item.dailyPrice ? ` · ${formatCurrency(item.dailyPrice)}/dia` : ''}</span></div></div><span className={`status-badge ${badgeClass}`}><span />{statusLabels[item.status] || item.status}</span></div>
    <div className="request-card-info"><Info label="Período solicitado" value={`${formatDate(item.startDate)} — ${formatDate(item.endDate)}`} /><Info label="Categoria" value={item.category || '—'} /><Info label={client ? 'Solicitado em' : 'Cliente'} value={client ? formatDate(item.submittedAt) : item.clientName} /><Info label="Agente" value={item.evaluatedBy || 'Aguardando agente'} /></div>
    {!client && <div className="applicant-details"><div className="applicant-heading"><span>PERFIL FINANCEIRO</span><strong>{item.clientName}</strong></div><div className="applicant-facts"><span>CPF <strong>{maskCpf(item.clientCpf)}</strong></span><span>RG <strong>{item.clientRg || '—'}</strong></span><span>Profissão <strong>{item.profession || '—'}</strong></span><span>Endereço <strong>{item.clientAddress || '—'}</strong></span></div><div className="income-list">{item.employments?.length ? item.employments.map((income) => <span key={income.id}><b>{income.employer}</b><small>{formatCurrency(income.income)} / mês</small></span>) : <span className="no-income">Nenhum vínculo de renda informado.</span>}</div></div>}
    {item.financialOpinion && <div className="opinion-box"><span>✳</span><div><strong>Parecer financeiro</strong><p>{item.financialOpinion}</p></div></div>}
    <div className="request-card-footer"><span className="updated-at">Atualizado em {formatDate(item.updatedAt)}</span><div className="action-row">
      {client && item.status === 'PENDENTE_ANALISE' && <button className="button button-small button-outline" onClick={() => onEdit(item)}>Editar pedido</button>}
      {client && !['CONTRATO_EXECUTADO', 'CANCELADO'].includes(item.status) && <button className="button button-small button-ghost-danger" onClick={() => onCancel(item)}>Cancelar</button>}
      {!client && item.status === 'PENDENTE_ANALISE' && <button className="button button-small button-primary" onClick={() => onReview(item)}>Iniciar análise <span>→</span></button>}
      {!client && item.status === 'EM_ANALISE' && <><button className="button button-small button-outline" onClick={() => onModifyAgent(item)}>Editar dados</button><button className="button button-small button-ghost-danger" onClick={() => onAssess(item, false)}>Recusar</button><button className="button button-small button-primary" onClick={() => onAssess(item, true)}>Aprovar <span>→</span></button></>}
      {!client && item.status === 'AGUARDANDO_EXECUCAO' && <button className="button button-small button-primary" onClick={() => onExecute(item)}>Executar contrato <span>→</span></button>}
    </div></div>
  </article>
}

function RequestRow({ item, compact }) {
  return <div className="recent-row"><div className="recent-car-icon"><Icon name="car" /></div><div className="recent-main"><strong>{item.make} {item.model}</strong><small>{formatDate(item.startDate)} <span>→</span> {formatDate(item.endDate)}</small></div><span className={`status-badge status-small ${statusClass(item.status)}`}><span />{statusLabels[item.status] || item.status}</span><span className="row-arrow">↗</span></div>
}

function RentalForm({ item, onSubmit, onCancel }) {
  const [form, setForm] = useState({
    carId: item?.carId || '', startDate: item?.startDate || plusDays(7),
    endDate: item?.endDate || plusDays(14), clientNotes: item?.clientNotes || '',
  })
  const [filters, setFilters] = useState({ query: '', category: '', transmission: '', fuel: '', maxDailyPrice: '', sort: 'recommended' })
  const [cars, setCars] = useState([])
  const [loadingCars, setLoadingCars] = useState(false)
  const [searchedPeriod, setSearchedPeriod] = useState('')
  const [error, setError] = useState('')
  const periodKey = `${form.startDate}|${form.endDate}`
  const setFilter = (key, value) => setFilters((current) => ({ ...current, [key]: value }))
  const setPeriod = (key, value) => setForm((current) => ({ ...current, [key]: value, carId: '' }))

  async function searchCars() {
    setError('')
    if (!form.startDate || !form.endDate || form.endDate <= form.startDate) {
      setError('Escolha um período válido para consultar a disponibilidade.')
      return
    }
    setLoadingCars(true)
    try {
      const params = new URLSearchParams({ startDate: form.startDate, endDate: form.endDate })
      if (item?.id) params.set('excludeRequestId', item.id)
      const result = await api(`/cars/available?${params.toString()}`)
      setCars(result)
      setSearchedPeriod(periodKey)
      if (form.carId && !result.some((car) => car.id === form.carId)) {
        setForm((current) => ({ ...current, carId: '' }))
      }
    } catch (cause) {
      setError(cause.message)
    } finally {
      setLoadingCars(false)
    }
  }

  useEffect(() => { searchCars() }, [])

  const normalize = (value) => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
  const visibleCars = useMemo(() => {
    const query = normalize(filters.query.trim())
    const filtered = cars.filter((car) => {
      const text = normalize(`${car.make} ${car.model} ${car.category}`)
      return (!query || text.includes(query))
        && (!filters.category || car.category === filters.category)
        && (!filters.transmission || car.transmission === filters.transmission)
        && (!filters.fuel || car.fuel === filters.fuel)
        && (!filters.maxDailyPrice || Number(car.dailyPrice) <= Number(filters.maxDailyPrice))
    })
    if (filters.sort === 'price-low') filtered.sort((a, b) => Number(a.dailyPrice) - Number(b.dailyPrice))
    if (filters.sort === 'price-high') filtered.sort((a, b) => Number(b.dailyPrice) - Number(a.dailyPrice))
    if (filters.sort === 'year') filtered.sort((a, b) => b.year - a.year)
    return filtered
  }, [cars, filters])

  const selectedCar = cars.find((car) => car.id === form.carId)
  const rentalDays = Math.max(1, Math.round((new Date(`${form.endDate}T00:00:00`) - new Date(`${form.startDate}T00:00:00`)) / 86400000))
  const submit = async (event) => {
    event.preventDefault()
    if (!form.startDate || !form.endDate || form.endDate <= form.startDate) { setError('A data final deve ser posterior à data inicial.'); return }
    if (searchedPeriod !== periodKey) { setError('Busque novamente após alterar as datas para confirmar a disponibilidade.'); return }
    if (!form.carId) { setError('Selecione um veículo disponível.'); return }
    try { await onSubmit({ carId: form.carId, startDate: form.startDate, endDate: form.endDate, clientNotes: form.clientNotes || null }) }
    catch (cause) { setError(cause.message) }
  }

  return <form className="modal-form rental-search-form" onSubmit={submit}>
    <div className="rental-search-intro"><div><span className="eyebrow muted">FROTA ROTA / 20 VEÍCULOS</span><h3>Encontre o carro ideal</h3><p>Escolha as datas para ver somente os veículos disponíveis nesse período.</p></div><div className="fleet-count"><strong>{cars.length}</strong><span>disponíveis</span></div></div>
    {error && <div className="inline-error">{error}</div>}
    <div className="rental-date-row">
      <Field label="Retirada" type="date" min={today()} value={form.startDate} onChange={(value) => setPeriod('startDate', value)} required />
      <Field label="Devolução" type="date" min={form.startDate} value={form.endDate} onChange={(value) => setPeriod('endDate', value)} required />
      <button type="button" className="button button-primary search-submit" onClick={searchCars} disabled={loadingCars}>{loadingCars ? <span className="spinner" /> : <Icon name="search" />} Buscar disponibilidade</button>
    </div>
    <div className="vehicle-filter-bar">
      <label className="vehicle-query"><Icon name="search" /><input value={filters.query} onChange={(event) => setFilter('query', event.target.value)} placeholder="Marca, modelo ou categoria" aria-label="Buscar por marca, modelo ou categoria" /></label>
      <select aria-label="Categoria" value={filters.category} onChange={(event) => setFilter('category', event.target.value)}><option value="">Todas as categorias</option>{uniqueValues(cars, 'category').map((value) => <option key={value}>{value}</option>)}</select>
      <select aria-label="Transmissão" value={filters.transmission} onChange={(event) => setFilter('transmission', event.target.value)}><option value="">Transmissão</option>{uniqueValues(cars, 'transmission').map((value) => <option key={value}>{value}</option>)}</select>
      <select aria-label="Combustível" value={filters.fuel} onChange={(event) => setFilter('fuel', event.target.value)}><option value="">Combustível</option>{uniqueValues(cars, 'fuel').map((value) => <option key={value}>{value}</option>)}</select>
      <Field label="Até R$/dia" type="number" min="0" step="10" value={filters.maxDailyPrice} onChange={(value) => setFilter('maxDailyPrice', value)} />
      <select aria-label="Ordenar carros" value={filters.sort} onChange={(event) => setFilter('sort', event.target.value)}><option value="recommended">Recomendados</option><option value="price-low">Menor diária</option><option value="price-high">Maior diária</option><option value="year">Mais novos</option></select>
    </div>
    <div className="vehicle-results-head"><strong>{visibleCars.length} {visibleCars.length === 1 ? 'carro encontrado' : 'carros encontrados'}</strong><span>{searchedPeriod === periodKey ? `${formatDate(form.startDate)} – ${formatDate(form.endDate)}` : 'Atualize a disponibilidade'}</span></div>
    {loadingCars ? <div className="loading-box"><span className="spinner spinner-dark"/>Consultando disponibilidade...</div> : visibleCars.length ? <div className="vehicle-grid">{visibleCars.map((car) => <button type="button" key={car.id} className={`vehicle-card ${form.carId === car.id ? 'selected' : ''}`} onClick={() => setForm((current) => ({ ...current, carId: car.id }))}>
      <div className="vehicle-image"><img src={car.imageUrl} alt={`${car.make} ${car.model}`} loading="lazy" onError={(event) => { event.currentTarget.style.visibility = 'hidden' }} /><span className="availability-chip"><i /> Disponível</span>{form.carId === car.id && <span className="selected-chip">✓ Selecionado</span>}</div>
      <div className="vehicle-card-copy"><div className="vehicle-title-row"><div><span className="vehicle-category">{car.category}</span><h4>{car.make} {car.model}</h4><p>{car.year} <i>·</i> {car.transmission} <i>·</i> {car.fuel}</p></div><span className="vehicle-rate"><strong>{formatCurrency(car.dailyPrice)}</strong><small>/ dia</small></span></div></div>
    </button>)}</div> : <div className="vehicle-empty"><div className="empty-art"><Icon name="car" /></div><h4>{cars.length ? 'Nenhum carro com esses filtros' : 'Nenhum veículo disponível nesse período'}</h4><p>{cars.length ? 'Altere a categoria, transmissão, combustível ou valor máximo.' : 'Tente novas datas para encontrar opções disponíveis.'}</p></div>}
    {selectedCar && <div className="rental-selection"><img src={selectedCar.imageUrl} alt="" /><div><span>SEU CARRO</span><strong>{selectedCar.make} {selectedCar.model}</strong><small>{rentalDays} {rentalDays === 1 ? 'diária' : 'diárias'} · {formatCurrency(selectedCar.dailyPrice)} por dia</small></div><b>{formatCurrency(Number(selectedCar.dailyPrice) * rentalDays)}</b></div>}
    <label className="field notes-field"><span>Observações para o agente <small>(opcional)</small></span><textarea rows="2" maxLength="1000" value={form.clientNotes} onChange={(event) => setForm((current) => ({ ...current, clientNotes: event.target.value }))} placeholder="Conte algo importante sobre sua viagem" /></label>
    <div className="modal-actions"><button type="button" className="button button-outline" onClick={onCancel}>Voltar</button><button className="button button-primary" disabled={!form.carId || searchedPeriod !== periodKey || loadingCars}>{item ? 'Salvar alterações' : 'Enviar pedido'} <span>→</span></button></div>
  </form>
}

function AgentEditForm({ item, onSubmit, onCancel }) {
  const [form, setForm] = useState({ startDate: item.startDate, endDate: item.endDate, clientNotes: item.clientNotes || '' })
  const [error, setError] = useState('')
  const submit = async (event) => {
    event.preventDefault()
    if (form.endDate <= form.startDate) { setError('A data final deve ser posterior à data inicial.'); return }
    try { await onSubmit({ ...form, clientNotes: form.clientNotes || null }) } catch (cause) { setError(cause.message) }
  }
  return <form className="modal-form" onSubmit={submit}>
    <div className="form-intro"><span className="form-icon"><Icon name="clock" /></span><p>Atualize o período e as observações sem alterar o veículo selecionado.</p></div>
    {error && <div className="inline-error">{error}</div>}
    <div className="field-grid"><Field label="Retirada" type="date" min={today()} value={form.startDate} onChange={(value) => setForm((current) => ({ ...current, startDate: value }))} required /><Field label="Devolução" type="date" min={form.startDate} value={form.endDate} onChange={(value) => setForm((current) => ({ ...current, endDate: value }))} required /><div className="span-2"><label className="field"><span>Observações</span><textarea rows="3" maxLength="1000" value={form.clientNotes} onChange={(event) => setForm((current) => ({ ...current, clientNotes: event.target.value }))} /></label></div></div>
    <div className="modal-actions"><button type="button" className="button button-outline" onClick={onCancel}>Voltar</button><button className="button button-primary">Salvar alterações <span>→</span></button></div>
  </form>
}

function ExecuteForm({ banks, onSubmit, onCancel }) {
  const [form, setForm] = useState({ ownerType: 'CLIENT', ownerName: '', withCredit: false, bankAgentId: '', creditAmount: '' })
  const [error, setError] = useState('')
  const set = (key, value) => setForm((current) => ({ ...current, [key]: value }))
  const submit = async (event) => {
    event.preventDefault()
    if (form.withCredit && !banks.length) { setError('Cadastre um banco agente antes de associar crédito.'); return }
    try { await onSubmit({ ownerType: form.ownerType, ownerName: form.ownerName, creditAmount: form.withCredit ? Number(form.creditAmount) : null, bankAgentId: form.withCredit ? form.bankAgentId : null }) }
    catch (cause) { setError(cause.message) }
  }
  return <form className="modal-form" onSubmit={submit}>
    <div className="form-intro"><span className="form-icon"><Icon name="check" /></span><p>Confirme os dados para registrar a execução do contrato.</p></div>
    {error && <div className="inline-error">{error}</div>}
    <label className="field"><span>Tipo de proprietário do automóvel</span><select value={form.ownerType} onChange={(e) => set('ownerType', e.target.value)}><option value="CLIENT">Cliente</option><option value="COMPANY">Empresa</option><option value="BANK">Banco</option></select></label>
    <Field label="Nome do proprietário" value={form.ownerName} onChange={(v) => set('ownerName', v)} required />
    <label className="checkbox-line"><input type="checkbox" checked={form.withCredit} onChange={(e) => set('withCredit', e.target.checked)} /> <span>Associar contrato de crédito concedido por banco agente</span></label>
    {form.withCredit && <div className="field-grid"><label className="field"><span>Banco concedente</span><select value={form.bankAgentId} onChange={(e) => set('bankAgentId', e.target.value)} required><option value="">Selecione um banco</option>{banks.map((bank) => <option value={bank.id} key={bank.id}>{bank.entityName}</option>)}</select></label><Field label="Valor do crédito (R$)" type="number" min="0.01" step="0.01" value={form.creditAmount} onChange={(v) => set('creditAmount', v)} required /></div>}
    <div className="modal-actions"><button type="button" className="button button-outline" onClick={onCancel}>Voltar</button><button className="button button-primary">Registrar contrato <span>→</span></button></div>
  </form>
}

function Modal({ title, children, onClose, wide = false }) {
  useEffect(() => {
    const close = (event) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [onClose])
  return <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className={`modal ${wide ? 'modal-wide' : ''}`} role="dialog" aria-modal="true"><div className="modal-header"><div><div className="eyebrow muted">ROTA / PEDIDOS</div><h2>{title}</h2></div><button className="icon-button close-button" onClick={onClose} aria-label="Fechar">×</button></div>{children}</section></div>
}

function Field({ label, value, onChange, type = 'text', required = false, autoComplete, min, max, step }) {
  return <label className="field"><span>{label}</span><input type={type} value={value ?? ''} required={required} autoComplete={autoComplete} min={min} max={max} step={step} onChange={(event) => onChange(event.target.value)} /></label>
}

function StatCard({ label, value, icon, accent, hint }) {
  return <article className="stat-card"><div className={`stat-icon ${accent}`}><Icon name={icon} /></div><div className="stat-copy"><span>{label}</span><strong>{value}</strong><small>{hint}</small></div><span className="stat-arrow">↗</span></article>
}

function Info({ label, value }) { return <div className="request-info"><span>{label}</span><strong>{value || '—'}</strong></div> }
function EmptyState({ title, description, action }) { return <div className="empty-state"><div className="empty-art"><Icon name="car" /></div><h3>{title}</h3><p>{description}</p>{action}</div> }
function uniqueValues(items, key) { return [...new Set(items.map((item) => item[key]).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'pt-BR')) }
function roleLabel(role) { return role === 'CLIENT' ? 'Cliente' : role === 'BANK_AGENT' ? 'Agente bancário' : 'Agente de empresa' }
function viewLabel(view, client) { return view === 'assistant' ? 'Conversar com agente' : view === 'profile' ? 'Meu cadastro' : view === 'requests' ? (client ? 'Meus pedidos' : 'Pedidos') : 'Visão geral' }
function statusClass(status) { return ['APROVADO', 'AGUARDANDO_EXECUCAO', 'CONTRATO_EXECUTADO'].includes(status) ? 'status-green' : status === 'REPROVADO' || status === 'CANCELADO' ? 'status-red' : status === 'EM_ANALISE' ? 'status-blue' : 'status-amber' }
function formatDate(value) { if (!value) return '—'; const date = new Date(value.length === 10 ? `${value}T12:00:00` : value); return new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' }).format(date).replace('.', '') }
function maskCpf(value = '') { const digits = value.replace(/\D/g, ''); return digits.length === 11 ? `${digits.slice(0, 3)}.***.***-${digits.slice(-2)}` : value }
function formatCurrency(value) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(Number(value || 0)) }

function CarIllustration({ compact = false }) {
  return <svg className={`car-illustration ${compact ? 'compact' : ''}`} viewBox="0 0 620 280" fill="none" aria-hidden="true"><ellipse cx="320" cy="245" rx="235" ry="23" fill="#101B2C" opacity=".16"/><path d="M91 183c9-22 26-40 51-47l44-12 59-57c12-12 28-19 45-19h109c27 0 50 12 67 34l42 55 31 11c20 7 34 26 34 47v26H78v-16c0-9 4-17 13-22Z" fill="#F3B44E"/><path d="m207 114 48-47c9-9 21-14 34-14h41v61H207Z" fill="#DDF0EF"/><path d="M347 53h52c20 0 37 9 50 25l28 36H347V53Z" fill="#DDF0EF"/><path d="M330 53v62" stroke="#B7802C" strokeWidth="6"/><path d="M96 174h83m327 0h37" stroke="#D98A27" strokeWidth="8" strokeLinecap="round"/><path d="M87 194c8-5 15-8 25-8h420c18 0 33 13 33 30v5H83v-8c0-8 1-15 4-19Z" fill="#E99B31"/><circle cx="178" cy="217" r="34" fill="#263347"/><circle cx="178" cy="217" r="18" fill="#E9EFF2"/><circle cx="486" cy="217" r="34" fill="#263347"/><circle cx="486" cy="217" r="18" fill="#E9EFF2"/><path d="M112 167c15-21 29-30 48-36" stroke="#F8D99D" strokeWidth="5" strokeLinecap="round"/><rect x="540" y="174" width="22" height="14" rx="7" fill="#FFF0D1"/><path d="M98 182h16" stroke="#F6F1E8" strokeWidth="6" strokeLinecap="round"/></svg>
}

function Icon({ name }) {
  const paths = {
    grid: <><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></>,
    search: <><circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 4.5 4.5"/></>,
    message: <><path d="M20 11.5a7.5 7.5 0 0 1-7.5 7.5 8 8 0 0 1-3.4-.8L4 20l1.4-4.3A7.5 7.5 0 1 1 20 11.5Z"/><path d="M8 11h8m-8 3h5"/></>,
    send: <><path d="m21 3-7.2 18-3.8-7-7-3.8L21 3Z"/><path d="M10 14 21 3"/></>,
    car: <><path d="m5 11 1.8-4.2A2 2 0 0 1 8.7 5.5h6.6a2 2 0 0 1 1.9 1.3L19 11"/><path d="M3.5 11h17v7h-17z"/><path d="M6 18v2m12-2v2M6 14h.01M18 14h.01"/><path d="M3.5 14H2m20 0h-1.5"/></>,
    user: <><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></>,
    logout: <><path d="M10 17l5-5-5-5m5 5H3"/><path d="M12 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6"/></>,
    clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
    check: <><circle cx="12" cy="12" r="9"/><path d="m8 12 2.5 2.5L16 9"/></>,
  }
  return <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] || paths.grid}</svg>
}

export default App
