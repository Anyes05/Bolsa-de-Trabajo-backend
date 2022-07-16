import moment from "moment";
import { Postulante } from "../models/Postulante";

export const templatePDF = async (host: string, data: Postulante): Promise<string> =>{
    return `
    <!DOCTYPE html>
    <html>

    <head>
        <meta charset="UTF-8">
        <meta http equiv="X-UA Compatible" content="IE-edge">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>PDF Curriculum</title>
        <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.1.2/dist/css/bootstrap.min.css" rel="stylesheet"
            integrity="sha384-uWxY/CJNBR+1zjPWmfnSnVxwRheevXITnMqoEIeG1LJrdI0GlVs/9cVSyPYXdcSF" crossorigin="anonymous">
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300&display=swap" rel="stylesheet">
        <style>
            body {
                font-family: 'Roboto', sans-serif;
            }
    
            h1 {
            color: navy;
            }

            .imagen{
                width: 100%;
                height: 250px;
                object-fit: cover;
                object-position: center;
            }

        </style>
    </head>

    <body>
        <div class="container p-5">

            <div class="card border-dark mb-3">
                <div class="row ">
                    <div class="col-md-8">
                        <div class="card-body">
                            <h2 class="card-title mb-4 mt-2">
                                ${data.primerNombre + " " + data.primerApellido}
                            </h2>
                            <div class="row">
                                <div class="col">
                                    <div class="col-12 col-md-6 mb-3"><strong>Tipo Documento:</strong> ${data.tipoDocumento}</div>
                                    <div class="col-12 col-md-6 mb-3"><strong>N° de Documento:</strong> ${data.documento}</div>
                                    <div class="col-12 col-md-6 mb-3"><strong>Fecha de nacimiento:</strong> ${moment(data.fechaNacimiento).format("DD/MM/YYYY")}</div>

                                </div>
                                <div class="col">
                                    <div class="col-12 col-md-6 mb-3"><strong>Sexo:</strong> ${data.sexo}</div>
                                    <div class="col-12 col-md-6 mb-3"><strong>Segundo Nombre:</strong> ${data.segundoNombre ?? '-'}</div>
                                    <div class="col-12 col-md-6 mb-3"><strong>Segundo Apellido:</strong> ${data.segundoApellido?? '-'}</div>
                                </div>

                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="card border-dark mb-3">
                <div class="card-body">
                    <h2 class="card-title mb-3">
                        Domicilio
                    </h2>
                    <div class="row">
                        <div class="col">
                            <div class="col-12 mb-2"><strong>País:</strong> ${data.pais.nombre}</div>
                            <div class="col-12 mb-2"><strong>Departamento:</strong> ${data.localidad?.departamento.nombre?? '-'}</div>
                            <div class="col-12 mb-2"><strong>Localidad:</strong> ${data.localidad?.nombre?? '-'}</div>

                        </div>
                        <div class="col">
                            <div class="col-12 mb-2"><strong>Barrio:</strong> ${data.barrio}</div>
                            <div class="col-12 mb-2"><strong>Dirección:</strong> ${data.direccion}</div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="card border-dark mb-3">
                <div class="card-body">
                    <h2 class="card-title mb-3">
                        Contacto
                    </h2>
                    <div class="row">
                        <div class="col">
                            <div class="col-12 mb-2"><strong>Primer teléfono/celular:</strong> ${data.primerTelefono}</div>
                            <div class="col-12 mb-2"><strong>Segundo teléfono/celular:</strong> ${data.segundoTelefono}</div>
                        </div>
                        <div class="col">
                            <div class="col-12 mb-2"><strong>Correo electrónico:</strong> ${data.email}</div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="card border-dark mb-3">
                <div class="card-body">
                    <h2 class="card-title mb-3">
                        Educación y Formación
                    </h2>
                    <div class="row">
                        <div class="col">
                            <div class="col-12 mb-2"><strong>Nivel educativo alcanzado:</strong> ${data.nivelEducativo}</div>
                            <div class="col-12 mb-2"><strong>Estado:</strong> ${data.estadoNE}</div>
                        </div>
                        <div class="col">
                            <!--Si tiene Orientación-->
                            <div class="col-12 mb-2"><strong>Orientación:</strong> ${data.orientacionNE}</div>
                        </div>
                    </div>
                </div>
            </div>

            <!--Si tiene Cursos/Capacitaciones-->
            <div class="card border-dark mb-3">
                <div class="card-body">
                    <h2 class="card-title mb-3">
                        Cursos y Capacitaciones
                    </h2>
                    <!--Uno de estos por cada curso/capacitacion-->
                    <ul class="list-group">
                        ${data.capacitacionFormacion ? data.capacitacionFormacion.map(capacitaciones=>{
                            return `
                            <li class="list-group-item">
                                <div class="card p-3 border-secondary">
                                    <div class="row">
                                        <div class="col">
                                            <div class="col-12 mb-2"><strong>Nombre del curso:</strong> ${capacitaciones.nombre}</div>
                                            <div class="col-12 mb-2"><strong>Área temática:</strong>  ${capacitaciones.areaTematica}</div>
                                            <div class="col-12 mb-2"><strong>Institución Educativa:</strong>  ${capacitaciones.institucion}</div>
                                        </div>
                                        <div class="col">
                                            <div class="col-12 mb-2"><strong>Fecha de inicio:</strong>  ${moment(capacitaciones.fechaInicio).format("DD/MM/YYYY")}</div>
                                            <div class="col-12 mb-2"><strong>Duración :</strong>  ${capacitaciones.duracion}</div>
                                            <div class="col-12 mb-2"><strong>Estado del curso:</strong>  ${capacitaciones.estado}</div>
                                        </div>
                                    </div>
                                </div>
                            </li>
                                `;
                            })
                            : `
                            <li class="list-group-item">
                                <div class="content">
                                    No tiene
                                </div>
                            </li>
                            `
                        }
                    </ul>    
                    
                </div>
            </div>

            <!--Si tiene Conocimientos informáticos-->
            <div class="card border-dark mb-3">
                <div class="card-body">
                    <h2 class="card-title mb-3">
                        Conocimientos informáticos
                    </h2>
                    <!--Uno de estos por cada Conocimiento informático-->
                    <ul class="list-group">
                        ${data.conocimientoInfo ? data.conocimientoInfo.map(conocimientos=>{
                            return `
                            <li class="list-group-item">
                                <div class="card p-3 border-secondary">
                                    <div class="row">
                                        <div class="col">
                                            <div class="col-12 mb-2"><strong>Nombre de la aplicación:</strong> ${conocimientos.nombreApp}</div>
                                            <div class="col-12 mb-2"><strong>Categoría:</strong> ${conocimientos.categoria}</div>
                                        </div>
                                        <div class="col">
                                            <div class="col-12 mb-2"><strong>Nivel de conocimiento:</strong> ${conocimientos.nivelConocimiento}</div>
                                        </div>
                                    </div>
                                </div>
                            </li>
                            `;
                        })
                        : `
                        <li class="list-group-item">
                            <div class="content">
                                No tiene
                            </div>
                        </li>
                        `
                    }
                    </ul>
                </div>
            </div>

            <!--Si tiene Idiomas-->
            <div class="card border-dark mb-3">
                <div class="card-body">
                    <h2 class="card-title mb-3">
                        Idiomas
                    </h2>
                    <!--Uno de estos por cada Idioma-->
                    <ul class="list-group">
                        ${data.idioma ? data.idioma.map(idiomas=>{
                            return `
                            <li class="list-group-item">
                                <div class="card p-3 border-secondary">
                                    <div>
                                        <div class="row">
                                            <div class="col">
                                                <div class="col-12 mb-2"><strong>Idioma:</strong> ${idiomas.nombre}</div>
                                            </div>
                                            <div class="col">
                                                <!--Si tiene-->
                                                <div class="col-12 mb-2"><strong>Especificación:</strong> ${idiomas.especificacion}</div>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="row">
                                        <div class="col">
                                            <div class="col-12 mb-2"><strong>Habla / Conversación:</strong> ${idiomas.hablaConv}</div>
                                            <div class="col-12 mb-2"><strong>Comprensión auditiva:</strong> ${idiomas.compAud}</div>
                                        </div>
                                        <div class="col">
                                            <div class="col-12 mb-2"><strong>Comprensión lectora:</strong> ${idiomas.compLec}</div>
                                            <div class="col-12 mb-2"><strong>Escritura:</strong> ${idiomas.escritura}</div>
                                        </div>
                                    </div>
                                </div>
                            </li>
                                `;
                            })
                            : `
                            <li class="list-group-item">
                                <div class="content">
                                    No tiene
                                </div>
                            </li>
                            `
                        }
                    </ul>    
                </div>
            </div>

            <!--Si Experiencias Laborales-->
            <div class="card border-dark mb-3">
                <div class="card-body">
                    <h2 class="card-title mb-3">
                        Experiencias Laborales
                    </h2>
                    <!--Uno de estos por cada Experiencia Laboral-->
                    <ul class="list-group">
                        ${data.expLaboral ? data.expLaboral.map(experiencias=>{
                            return `
                            <li class="list-group-item">
                                <div class="card p-3 border-secondary">
                                    <div>
                                        <div class="row">
                                            <div class="col">
                                                <h5> Nombre de la empresa:</strong> ${experiencias.nombreEmp}</h5>
                                                <div class="col-12 mb-2"><strong>Cargo que ocupó:</strong> ${experiencias.cargo}</div>
                                                <div class="col-12 mb-2"><strong>Área o rubro:</strong> ${experiencias.area}</div>
                                            </div>
                                            <div class="col">
                                                <div class="col-12 mb-2"><strong>Nivel Jerárquico:</strong> ${experiencias.nivelJer}</div>
                                                <div class="col-12 mb-2"><strong>Tareas realizadas:</strong> ${experiencias.tareas}</div>
                                                <div class="col-12 mb-2"><strong>Período trabajado:</strong> ${"Del " + moment(experiencias.fechaInicio).format("DD/MM/YYYY") + " al " + moment(experiencias.fechaFin).format("DD/MM/YYYY")}</div>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="row mt-2 mb-1">
                                        <h5>Referencia Laboral</h5>
                                        <div class="col">
                                            <div class="col-12 mb-2"><strong>Nombre :</strong> ${experiencias.nombreRef}</div>
                                            <div class="col-12 mb-2"><strong>Apellido:</strong> ${experiencias.apellidoRef}</div>
                                            <div class="col-12 mb-2"><strong>Cargo:</strong> ${experiencias.cargoRef}</div>
                                        </div>
                                        <div class="col">
                                            <div class="col-12 mb-2"><strong>Teléfono/Celular:</strong> ${experiencias.telefonoRef}</div>
                                            <div class="col-12 mb-2"><strong>Correo electrónico:</strong> ${experiencias.emailRef}</div>
                                        </div>
                                    </div>
                                </div>
                            </li>
                                `;
                            })
                            : `
                            <li class="list-group-item">
                                <div class="content">
                                    No tiene
                                </div>
                            </li>
                            `
                        }
                    </ul>       
                </div>
            </div>

            <!--Si tiene Permisos y Licencias-->
            <div class="card border-dark mb-3">
                <div class="card-body">
                    <h2 class="card-title mb-3">
                        Permisos y Licencias
                    </h2>
                    <!--Uno de estos por cada Permiso/Licencia-->
                    <ul class="list-group">
                        ${data.permisosLicencias ? data.permisosLicencias.map(permisosLicencias=>{
                            return `
                            <li class="list-group-item">
                                <div class="card p-3 border-secondary">
                                    <div>
                                        <div class="row">
                                            <div class="col">
                                                <div class="col-12 mb-2"><strong>Tipo de documento:</strong> ${permisosLicencias.tipoDocumento}</div>
                                                <!--Si tiene-->
                                                <div class="col-12 mb-2"><strong>Especificación:</strong> ${permisosLicencias.especificacion}</div>
                                            </div>
                                            <div class="col">
                                                <div class="col-12 mb-2"><strong>Vigencia:</strong> ${moment(permisosLicencias.vigencia).format("DD/MM/YYYY")}</div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </li>
                                `;
                            })
                            : `
                            <li class="list-group-item">
                                <div class="content">
                                    No tiene
                                </div>
                            </li>
                            `
                        }
                    </ul>      
                </div>
            </div>

            
            <div class="card border-dark mb-3">
                <div class="card-body">
                    <h2 class="card-title mb-3">
                        Intereses y Preferencias
                    </h2>
                    <h5>Jornada laboral preferida:</h5>
                    <div class="mb-3">${`${data.jCompleta?"Completa ":""} ${data.jIndiferente?"Indiferente":""} ${data.jMtManiana?"Mañana ":""} ${data.jMtTarde?"Tarde":""} ${data.jMtNoche?"Noche ":""} `}</div>

                    <!--Si tiene Preferencias laborales-->
                    <!--Uno de estos por cada Preferencia Laboral-->
                    <ul class="list-group">
                        ${data.preferenciaLaboral ? data.preferenciaLaboral.map(preferencias=>{
                            return `
                            <li class="list-group-item">
                                <div class="card p-3 border-secondary">
                                    <div>
                                        <div class="row">
                                            <div class="col">
                                                <div class="col-12 mb-2"><strong>Puesto preferido:</strong> ${preferencias.puestoPreferido}</div>
                                                <div class="col-12 mb-2"><strong>Área de interés laboral:</strong> ${preferencias.areaInteres}</div>
                                            </div>
                                            <div class="col">
                                                <div class="col-12 mb-2"><strong>Aspiración salarial nominal:</strong> ${preferencias.aspiracionSalarial}</div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </li>
                                `;
                            })
                            : `
                            <li class="list-group-item">
                                <div class="content">
                                    No tiene
                                </div>
                            </li>
                            `
                        }
                    </ul>     
                </div>
            </div>

        </div>

        </div>

</body>

</html>
    `
}