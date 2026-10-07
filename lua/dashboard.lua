return ui.column {
  gap = 20,
  children = {
    ui.text { text = "OPERATIONS · LIVE", style = "body" },
    ui.text { text = "Good morning, team", style = "title" },
    ui.text { text = "A clear view of today's product health." },
    ui.card {
      children = {
        ui.column {
          gap = 6,
          children = {
            ui.text { text = "Active users" },
            ui.text { text = tostring(state.activeUsers), style = "metric" },
            ui.text { text = state.activityNote }
          }
        }
      }
    },
    ui.row {
      gap = 12,
      children = {
        ui.card {
          children = {
            ui.column {
              gap = 6,
              children = {
                ui.text { text = "Revenue" },
                ui.text { text = state.revenue, style = "metric" },
                ui.text { text = "This month" }
              }
            }
          }
        },
        ui.card {
          children = {
            ui.column {
              gap = 6,
              children = {
                ui.text { text = "Reliability" },
                ui.text { text = state.reliability, style = "metric" },
                ui.text { text = "Last 30 days" }
              }
            }
          }
        }
      }
    },
    ui.card {
      children = {
        ui.column {
          gap = 6,
          children = {
            ui.text { text = "Systems healthy", style = "title" },
            ui.text { text = "All critical services are operating normally." }
          }
        }
      }
    },
    ui.button { text = "Refresh dashboard", action = "refresh" },
    ui.text { text = state.refreshedAt }
  }
}
