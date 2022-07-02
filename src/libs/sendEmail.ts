import nodemailer from "nodemailer";

export const sendEmail = async (to: string, asunto: string, html: string): Promise<boolean> => {


    var transporter = nodemailer.createTransport({
        host: "smtp.gmail.com",
        port: 465,
        secure: true,
        auth: {
            user: process.env.SMTP_GMAIL_MAIL,
            pass: process.env.SMTP_GMAIL_PASS
        }
    });

    var mailOptions = {
        from: "Centro Empleo <" + process.env.SMTP_GMAIL_MAIL + ">",
        to: to,
        subject: asunto,
        html: html
    }

    transporter.verify().then(() => {
        console.log('Ready for send emails');
    })

    transporter.sendMail(mailOptions, (error: any, info: any) => {
        if (error) {
            return false
        } else {
            console.log("Email enviado");
            return true;
        }
    })
    return false;
}