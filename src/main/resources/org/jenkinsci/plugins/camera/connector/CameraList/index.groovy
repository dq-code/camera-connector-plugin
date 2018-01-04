package org.jenkinsci.plugins.camera.connector.CameraList

def l = namespace(lib.LayoutTagLib)
def f = namespace(lib.FormTagLib)

l.layout {
    def title = _("Connected Cameras");
    l.header(title:title)
    l.main_panel {
        h1 {
            img(src:"${resURL}/plugin/camera-connector/icons/48x48/camera.png",alt:"[!]",height:48,width:48)
            text " "
            text title
        }

        p _("blurb")

        table(id:"devices", class:"sortable pane bigtable") {
            tr {
                th(width:32) {
                    // column to show icon
                }
                th(initialSortDir:"down") {
                    text _("Device Name")
                }
                th {
                    text _("Connected to")
                }
            }
            my.devices.entries().each { e ->
                def dev = e.value;
                tr {
                    td {
                        a(href:dev.deviceName) {
                            img(src:"${resURL}/plugin/camera-connector/icons/24x24/camera.png")
                        }
                    }
                    td {
                        text dev.deviceName;
                    }
                    td {
                        a(href:"$rootURL/${e.key.url}",  e.key.name)
                    }
                }
            }
        }

        f.form(method:"POST",action:"refresh") {
            div (align:"right",style:"margin-top:1em") {
                f.submit(value:_("Refresh"))
            }
        }
    }
}